package my_base;

import db.ExcelDB;
import team.domain.backend.TeamBackend;

/*
 * This class should hold the content of the system, i.e., all elements that are
 * related to the essence of the system.
 * 
 */
public class AppContent {
	private TeamBackend teamBackend;

	public void initContent() {
		teamBackend = new TeamBackend();
		ExcelDB.getInstance().createTableFromExcel("events");
	};

	public TeamBackend teamBackend() {
		return teamBackend;
	}
}
