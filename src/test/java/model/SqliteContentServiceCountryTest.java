package model;

import controller.ProgramControlException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Countries, and the country a facility is assigned to.
 *
 * A country is an ordinary content type, so listing, reading, creating and
 * updating one travel the paths the other types already travel. The facility
 * relationship is modelled the way mission_facilities is: a column the service
 * reads through, not a value copied onto the facility row, so renaming a
 * country is visible at every facility assigned to it.
 */
class SqliteContentServiceCountryTest {

    private static final String SUPPLIED = "Afghanistan\nAlbania\nAlgeria\nFrance\nUnited Kingdom\n";

    @TempDir Path temporary;

    private final Session admin = new Session("admin", Role.ADMIN);
    private final Session reader = new Session("reader", Role.STANDARD);

    /** A service whose countries come from the text supplied at the file boundary. */
    private SqliteContentService service(String countryText) throws Exception {
        Path database = temporary.resolve("countries.db");
        SqliteAuditLogRepository audit = new SqliteAuditLogRepository(database);
        audit.initialize();
        FileHandler files = mock(FileHandler.class);
        when(files.readText(SqliteContentService.COUNTRIES_TXT)).thenReturn(countryText);
        SqliteContentService service = new SqliteContentService(database, audit, files);
        service.initialize();
        return service;
    }

    private SqliteContentService service() throws Exception {
        return service(SUPPLIED);
    }

    @Test
    void theSuppliedListIsImportedWhenTheTableIsEmpty() throws Exception {
        SqliteContentService service = service();

        List<ContentEntry> countries = service.list(ContentType.COUNTRY, reader);

        assertEquals(5, countries.size());
        assertEquals("Afghanistan", countries.get(0).values().get(0));
    }

    @Test
    void aMissingCountryFileLeavesTheTableEmptyRatherThanFailing() throws Exception {
        SqliteContentService service = service(null);

        assertEquals(List.of(), service.list(ContentType.COUNTRY, reader));
    }

    @Test
    void blankLinesInTheSuppliedListAreIgnored() throws Exception {
        SqliteContentService service = service("France\n\n   \nAlbania\n");

        assertEquals(2, service.list(ContentType.COUNTRY, reader).size());
    }

    @Test
    void importingTwiceDoesNotDuplicateTheList() throws Exception {
        SqliteContentService service = service();

        service.initialize();

        assertEquals(5, service.list(ContentType.COUNTRY, reader).size());
    }

    @Test
    void aCountryIsReadByItsIdentifier() throws Exception {
        SqliteContentService service = service();
        int id = service.list(ContentType.COUNTRY, reader).get(3).id();

        assertEquals(List.of("France"), service.read(ContentType.COUNTRY, id, reader).values());
    }

    @Test
    void anAdministratorCreatesACountry() throws Exception {
        SqliteContentService service = service();

        service.add(ContentType.COUNTRY, List.of("Andorra"), admin);

        assertTrue(service.list(ContentType.COUNTRY, reader).stream()
                .anyMatch(entry -> entry.values().get(0).equals("Andorra")));
    }

    @Test
    void anAdministratorRenamesACountry() throws Exception {
        SqliteContentService service = service();
        int id = service.list(ContentType.COUNTRY, reader).get(3).id();

        service.update(ContentType.COUNTRY, id, List.of("France (Metropolitan)"), admin);

        assertEquals(List.of("France (Metropolitan)"),
                service.read(ContentType.COUNTRY, id, reader).values());
    }

    @Test
    void aStandardUserMayReadButNotChangeACountry() throws Exception {
        SqliteContentService service = service();
        int id = service.list(ContentType.COUNTRY, reader).get(0).id();

        assertEquals("Afghanistan", service.read(ContentType.COUNTRY, id, reader).values().get(0));
        assertThrows(ProgramControlException.class,
                () -> service.update(ContentType.COUNTRY, id, List.of("Elsewhere"), reader));
        assertThrows(ProgramControlException.class,
                () -> service.add(ContentType.COUNTRY, List.of("Atlantis"), reader));
    }

    @Test
    void changingACountryIsAudited() throws Exception {
        SqliteContentService service = service();
        int id = service.list(ContentType.COUNTRY, reader).get(0).id();

        service.update(ContentType.COUNTRY, id, List.of("Afghanistan"), admin);

        assertTrue(service.audit(admin).stream().anyMatch(entry ->
                entry.getContentType() == AuditContentType.COUNTRY
                        && entry.getEventType() == AuditEventType.UPDATE
                        && entry.getUsername().equals("admin")));
    }

    /* The facility relationship. */

    private int facility(SqliteContentService service) throws Exception {
        service.add(ContentType.FACILITY, List.of("Blackwood Annex", "BWA"), admin);
        return service.list(ContentType.FACILITY, reader).get(0).id();
    }

    private int country(SqliteContentService service, String name) throws Exception {
        return service.list(ContentType.COUNTRY, reader).stream()
                .filter(entry -> entry.values().get(0).equals(name))
                .findFirst().orElseThrow().id();
    }

    @Test
    void aNewFacilityHasNoCountryUntilOneIsAssigned() throws Exception {
        SqliteContentService service = service();

        assertEquals(Optional.empty(), service.countryFor(facility(service), reader));
    }

    @Test
    void anAssignedCountryIsReadBackForTheFacility() throws Exception {
        SqliteContentService service = service();
        int facility = facility(service);

        service.setCountry(facility, country(service, "France"), admin);

        assertEquals("France",
                service.countryFor(facility, reader).orElseThrow().values().get(0));
    }

    @Test
    void reassigningAFacilityReplacesItsCountry() throws Exception {
        SqliteContentService service = service();
        int facility = facility(service);
        service.setCountry(facility, country(service, "France"), admin);

        service.setCountry(facility, country(service, "Albania"), admin);

        assertEquals("Albania",
                service.countryFor(facility, reader).orElseThrow().values().get(0));
    }

    @Test
    void renamingACountryShowsThroughAtItsFacilities() throws Exception {
        SqliteContentService service = service();
        int facility = facility(service);
        int france = country(service, "France");
        service.setCountry(facility, france, admin);

        service.update(ContentType.COUNTRY, france, List.of("Republic of France"), admin);

        assertEquals("Republic of France",
                service.countryFor(facility, reader).orElseThrow().values().get(0));
    }

    @Test
    void aCountryMayHoldSeveralFacilities() throws Exception {
        SqliteContentService service = service();
        service.add(ContentType.FACILITY, List.of("Blackwood Annex", "BWA"), admin);
        service.add(ContentType.FACILITY, List.of("Rose Garden", "RGD"), admin);
        List<ContentEntry> facilities = service.list(ContentType.FACILITY, reader);
        int france = country(service, "France");

        service.setCountry(facilities.get(0).id(), france, admin);
        service.setCountry(facilities.get(1).id(), france, admin);

        assertEquals("France",
                service.countryFor(facilities.get(0).id(), reader).orElseThrow().values().get(0));
        assertEquals("France",
                service.countryFor(facilities.get(1).id(), reader).orElseThrow().values().get(0));
    }

    @Test
    void theFacilityAndTheCountryMustBothExist() throws Exception {
        SqliteContentService service = service();
        int facility = facility(service);
        int france = country(service, "France");

        assertThrows(ProgramControlException.class, () -> service.setCountry(404, france, admin));
        assertThrows(ProgramControlException.class, () -> service.setCountry(facility, 404, admin));
    }

    @Test
    void aFacilityThatDoesNotExistHasNoCountry() throws Exception {
        SqliteContentService service = service();

        assertEquals(Optional.empty(), service.countryFor(404, reader));
    }

    @Test
    void aStandardUserMayReadButNotAssignACountry() throws Exception {
        SqliteContentService service = service();
        int facility = facility(service);
        int france = country(service, "France");
        service.setCountry(facility, france, admin);

        assertTrue(service.countryFor(facility, reader).isPresent());
        assertThrows(ProgramControlException.class,
                () -> service.setCountry(facility, country(service, "Albania"), reader));
    }

    @Test
    void assigningACountryIsAuditedAgainstTheFacility() throws Exception {
        SqliteContentService service = service();
        int facility = facility(service);

        service.setCountry(facility, country(service, "France"), admin);

        assertTrue(service.audit(admin).stream().anyMatch(entry ->
                entry.getContentType() == AuditContentType.FACILITY
                        && entry.getEventType() == AuditEventType.UPDATE
                        && entry.getContentId() == facility));
    }

    @Test
    void aCountryStillHoldingFacilitiesIsNotDeleted() throws Exception {
        SqliteContentService service = service();
        int facility = facility(service);
        int france = country(service, "France");
        service.setCountry(facility, france, admin);

        assertThrows(ProgramControlException.class,
                () -> service.delete(ContentType.COUNTRY, france, admin));

        assertTrue(service.countryFor(facility, reader).isPresent());
    }

    /*
     * Creating a facility together with its country. A facility is assigned to
     * one country, and the new row's identifier is not known to the caller
     * until it is written, so the country has to be supplied in the same call
     * for the facility not to exist briefly without one. This mirrors
     * addMission.
     */

    @Test
    void aFacilityIsCreatedWithItsCountry() throws Exception {
        SqliteContentService service = service();

        int id = service.addFacility(List.of("Blackwood Annex", "BWA"),
                country(service, "France"), admin);

        assertEquals(List.of("Blackwood Annex", "BWA"),
                service.read(ContentType.FACILITY, id, reader).values());
        assertEquals("France", service.countryFor(id, reader).orElseThrow().values().get(0));
    }

    @Test
    void theIdentifierReturnedIsTheNewFacility() throws Exception {
        SqliteContentService service = service();
        int france = country(service, "France");

        int first = service.addFacility(List.of("Blackwood Annex", "BWA"), france, admin);
        int second = service.addFacility(List.of("Rose Garden", "RGD"), france, admin);

        assertTrue(second > first);
        assertEquals("Rose Garden",
                service.read(ContentType.FACILITY, second, reader).values().get(0));
    }

    @Test
    void anUnknownCountryLeavesNoFacilityBehind() throws Exception {
        SqliteContentService service = service();

        assertThrows(ProgramControlException.class,
                () -> service.addFacility(List.of("Nowhere", "NWH"), 404, admin));

        assertEquals(List.of(), service.list(ContentType.FACILITY, reader));
    }

    @Test
    void theWrongNumberOfFacilityFieldsLeavesNothingBehind() throws Exception {
        SqliteContentService service = service();

        assertThrows(ProgramControlException.class,
                () -> service.addFacility(List.of("Only a name"), country(service, "France"), admin));

        assertEquals(List.of(), service.list(ContentType.FACILITY, reader));
    }

    @Test
    void aStandardUserMayNotCreateAFacility() throws Exception {
        SqliteContentService service = service();

        assertThrows(ProgramControlException.class,
                () -> service.addFacility(List.of("Blackwood Annex", "BWA"),
                        country(service, "France"), reader));

        assertEquals(List.of(), service.list(ContentType.FACILITY, reader));
    }

    @Test
    void creatingAFacilityIsAuditedAsACreate() throws Exception {
        SqliteContentService service = service();

        int id = service.addFacility(List.of("Blackwood Annex", "BWA"),
                country(service, "France"), admin);

        assertTrue(service.audit(admin).stream().anyMatch(entry ->
                entry.getContentType() == AuditContentType.FACILITY
                        && entry.getEventType() == AuditEventType.CREATE
                        && entry.getContentId() == id));
    }

    @Test
    void updatingAFacilityChangesItsFieldsAndItsCountryTogether() throws Exception {
        SqliteContentService service = service();
        int id = service.addFacility(List.of("Blackwood Annex", "BWA"),
                country(service, "France"), admin);

        service.updateFacility(id, List.of("Blackwood Hall", "BWH"),
                country(service, "Albania"), admin);

        assertEquals(List.of("Blackwood Hall", "BWH"),
                service.read(ContentType.FACILITY, id, reader).values());
        assertEquals("Albania", service.countryFor(id, reader).orElseThrow().values().get(0));
    }

    @Test
    void anUnknownCountryLeavesTheFacilityUntouched() throws Exception {
        SqliteContentService service = service();
        int france = country(service, "France");
        int id = service.addFacility(List.of("Blackwood Annex", "BWA"), france, admin);

        assertThrows(ProgramControlException.class,
                () -> service.updateFacility(id, List.of("Renamed", "REN"), 404, admin));

        assertEquals(List.of("Blackwood Annex", "BWA"),
                service.read(ContentType.FACILITY, id, reader).values());
        assertEquals(france, service.countryFor(id, reader).orElseThrow().id());
    }

    @Test
    void aStandardUserMayNotUpdateAFacility() throws Exception {
        SqliteContentService service = service();
        int id = service.addFacility(List.of("Blackwood Annex", "BWA"),
                country(service, "France"), admin);

        assertThrows(ProgramControlException.class, () -> service.updateFacility(
                id, List.of("Renamed", "REN"), country(service, "Albania"), reader));

        assertEquals("Blackwood Annex",
                service.read(ContentType.FACILITY, id, reader).values().get(0));
    }

    @Test
    void updatingAnUnknownFacilityIsRefused() throws Exception {
        SqliteContentService service = service();

        assertThrows(ProgramControlException.class, () -> service.updateFacility(
                404, List.of("Ghost", "GHO"), country(service, "France"), admin));
    }

    @Test
    void updatingAFacilityIsAuditedAsAnUpdate() throws Exception {
        SqliteContentService service = service();
        int id = service.addFacility(List.of("Blackwood Annex", "BWA"),
                country(service, "France"), admin);

        service.updateFacility(id, List.of("Blackwood Hall", "BWH"),
                country(service, "Albania"), admin);

        assertTrue(service.audit(admin).stream().anyMatch(entry ->
                entry.getContentType() == AuditContentType.FACILITY
                        && entry.getEventType() == AuditEventType.UPDATE
                        && entry.getContentId() == id));
    }

    /*
     * Seeded facilities. The sample file may name a country; two-column files
     * written before countries existed stay valid and import unassigned.
     */

    private SqliteContentService seeded(String facilityText) throws Exception {
        Path database = temporary.resolve("seeded-" + Math.abs(facilityText.hashCode()) + ".db");
        SqliteAuditLogRepository audit = new SqliteAuditLogRepository(database);
        audit.initialize();
        FileHandler files = mock(FileHandler.class);
        when(files.readText(SqliteContentService.COUNTRIES_TXT)).thenReturn(SUPPLIED);
        when(files.readText(SqliteContentService.FACILITIES_TSV)).thenReturn(facilityText);
        SqliteContentService service = new SqliteContentService(database, audit, files);
        service.initialize();
        return service;
    }

    @Test
    void aSeededFacilityIsAssignedTheCountryItNames() throws Exception {
        SqliteContentService service = seeded("Blackwood Annex\tBWA\tFrance\n");

        int id = service.list(ContentType.FACILITY, reader).get(0).id();

        assertEquals("France", service.countryFor(id, reader).orElseThrow().values().get(0));
    }

    @Test
    void aTwoColumnFacilityFileStillImportsAndStaysUnassigned() throws Exception {
        SqliteContentService service = seeded("Blackwood Annex\tBWA\n");

        int id = service.list(ContentType.FACILITY, reader).get(0).id();

        assertEquals("Blackwood Annex",
                service.read(ContentType.FACILITY, id, reader).values().get(0));
        assertEquals(Optional.empty(), service.countryFor(id, reader));
    }

    /**
     * Every seed file is optional. Before countries existed, a directory with
     * facilities.tsv and no countries.txt started normally, and it still must:
     * a facility naming a country there is imported unassigned rather than
     * killing the application. A name missing from a list that WAS loaded is a
     * different thing and still fails, because that is a fault in our own
     * sample data.
     */
    @Test
    void facilitiesStillImportWhenNoCountryListWasSupplied() throws Exception {
        Path database = temporary.resolve("no-countries.db");
        SqliteAuditLogRepository audit = new SqliteAuditLogRepository(database);
        audit.initialize();
        FileHandler files = mock(FileHandler.class);
        when(files.readText(SqliteContentService.COUNTRIES_TXT)).thenReturn(null);
        when(files.readText(SqliteContentService.FACILITIES_TSV))
                .thenReturn("Blackwood Annex\tBWA\tNorway\n");
        SqliteContentService service = new SqliteContentService(database, audit, files);

        service.initialize();

        assertEquals(List.of(), service.list(ContentType.COUNTRY, reader));
        List<ContentEntry> facilities = service.list(ContentType.FACILITY, reader);
        assertEquals(1, facilities.size());
        assertEquals("Blackwood Annex", facilities.get(0).values().get(0));
        assertEquals(Optional.empty(), service.countryFor(facilities.get(0).id(), reader));
    }

    @Test
    void aSeededCountryThatIsNotInTheSuppliedListIsRefused() {
        assertThrows(ProgramControlException.class,
                () -> seeded("Blackwood Annex\tBWA\tAtlantis\n"));
    }

    @Test
    void facilityRowsOfOtherWidthsAreStillRefused() {
        assertThrows(ProgramControlException.class, () -> seeded("Only a name\n"));
        assertThrows(ProgramControlException.class,
                () -> seeded("Blackwood Annex\tBWA\tFrance\tExtra\n"));
    }

    @Test
    void theDeliveredFacilityFileNamesACountryForEverySite() {
        String delivered = new FileHandlerImpl().readText(SqliteContentService.FACILITIES_TSV);

        assertTrue(delivered != null && !delivered.isBlank(), "facilities.tsv should be readable");
        for (String line : delivered.replace("\r", "").split("\n")) {
            if (!line.isBlank()) {
                assertEquals(3, line.split("\t", -1).length,
                        "every delivered facility row should name a country: " + line);
            }
        }
    }

    @Test
    void aCountryWithNoFacilitiesIsDeleted() throws Exception {
        SqliteContentService service = service();
        int albania = country(service, "Albania");

        service.delete(ContentType.COUNTRY, albania, admin);

        assertFalse(service.list(ContentType.COUNTRY, reader).stream()
                .anyMatch(entry -> entry.values().get(0).equals("Albania")));
    }
}
