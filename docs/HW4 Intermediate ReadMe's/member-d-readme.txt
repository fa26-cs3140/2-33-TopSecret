MEMBER D - USER INTERFACE HANDOFF

Call ProgramControl.searchMissions(query) when the user selects Search. You can
choose the menu wording and layout.

The method returns matching Mission objects. Display a numbered list of their
titles. If the list is empty, display "No matches found." After the search,
keep the program running and show the menu again.

If ProgramControl throws an error, display its message without crashing or
closing the interactive menu.
