package gov.sbs.plugins.domain;

import gov.sbs.plugins.domain.Report.Subreport;
import gov.sbs.plugins.domain.Report.Subreport.Entry;

import java.util.Collections;
import java.util.Comparator;
import java.util.Date;

public class ViewGenerator {
	
	private static String page = "<html><head><title>Vanderblame Code Quality Tool</title><script type=\"text/javascript\">function changeTab(tab) {var mainTab = document.getElementById('MAIN_tab');var pmdTab = document.getElementById('PMD_tab');var fortifyTab = document.getElementById('FORTIFY_tab');var mainBody = document.getElementById('MAIN_body');var pmdBody = document.getElementById('PMD_body');var fortifyBody = document.getElementById('FORTIFY_body');if (tab == 'MAIN') {mainTab.className = \"tab tabSelected tabFirst\";mainBody.className = \"tabBody tabBodySelected\";} else {mainTab.className = \"tab tabFirst\";mainBody.className = \"tabBody tabBodyUnselected\";}if (tab == 'PMD') {pmdTab.className = \"tab tabSelected\";pmdBody.className = \"tabBody tabBodySelected\";} else {pmdTab.className = \"tab\";pmdBody.className = \"tabBody tabBodyUnselected\";}if (tab == 'FORTIFY') {fortifyTab.className = \"tab tabSelected tabLast\";fortifyBody.className = \"tabBody tabBodySelected\";} else {fortifyTab.className = \"tab tabLast\";fortifyBody.className = \"tabBody tabBodyUnselected\";}}</script><style type=\"text/css\">.tab {border-left: 1px solid #28288A;border-right: 1px solid #28288A;border-top: 1px solid #28288A;height: 30px;width: 100px;text-align: center;vertical-align: middle;display: table-cell;cursor: pointer;}.tabFirst {border-radius: 5px 0px 0px 0px;}.tabLast {border-radius: 0px 5px 0px 0px;}.tabSelected {background-color: #28288A;color: white;}.tabBody {width: 99%; height: 90%;border: 1px solid #28288A;padding: 5px; overflow: auto;}.tabBodySelected {display: block;}.tabBodyUnselected {display: none;}</style></head><body><div id=\"MAIN_tab\" class=\"tab tabSelected tabFirst\" onclick=\"changeTab('MAIN')\">Main</div><div id=\"PMD_tab\" class=\"tab\" onclick=\"changeTab('PMD')\">PMD</div><div id=\"FORTIFY_tab\" class=\"tab tabLast\" onclick=\"changeTab('FORTIFY')\">Fortify</div><div id=\"MAIN_body\" class=\"tabBody tabBodySelected\">{{MAIN_PLACEHOLDER}}</div><div id=\"PMD_body\" class=\"tabBody tabBodyUnselected\">{{PMD_PLACEHOLDER}}</div><div id=\"FORTIFY_body\" class=\"tabBody tabBodyUnselected\">{{FORTIFY_PLACEHOLDER}}</div></body></html>";
	
	private static String tableHeader = "<table style=\"width: 95%; border: #28288A 1px solid; margin: 3px;\">";
	private static String tableFooter = "</table>";
	
	public static String createReport(Report blameReport) {
		String pmdReport = "";
		String fortifyReport = "";
		
		String pmdMostGuilty = "";
		String fortifyMostGuilty = "";
		
		for (Subreport sr : blameReport.getSubreport()) {
			if (ReportType.PMD == sr.getName()) {
				pmdReport = createEntries(blameReport.getTimestamp().toGregorianCalendar().getTime(), sr);
				pmdMostGuilty = sr.getMostGuilty();
			}
			
			if (ReportType.FORTIFY == sr.getName()) {
				fortifyReport = createEntries(blameReport.getTimestamp().toGregorianCalendar().getTime(), sr);
				fortifyMostGuilty = sr.getMostGuilty();
			}
		}
		
		StringBuffer buffer = new StringBuffer();
		buffer.append("<ul>");
			buffer.append("<li>Leading PMD Violator is: <b>"+pmdMostGuilty+"</b></li>");
			buffer.append("<li>Leading Fortify Violator is: <b>"+fortifyMostGuilty+"</b></li>");
		buffer.append("</ul>");
		
		
		return page.replace("{{MAIN_PLACEHOLDER}}", buffer.toString()).replace("{{PMD_PLACEHOLDER}}", pmdReport).replace("{{FORTIFY_PLACEHOLDER}}", fortifyReport);
	}
	
	public static String createEntries(Date dateOfReport, Subreport sr) {
		StringBuffer sb = new StringBuffer();
		
		if (null == sr.getEntry() || sr.getEntry().size() == 0) {
			return "There are no " + sr.getName() + " violations.  Well done guys.";
		}
		
		//Make sure we sort the sub report before printing it
		Collections.sort(sr.getEntry(), new Comparator<Entry>() {
			@Override
			public int compare(Entry o1, Entry o2) {
				return o1.getGuiltyParty().compareTo(o2.getGuiltyParty());
			}
		});

		String lastGuilty = "";
		int counter = 0;
		
		for (Entry e : sr.getEntry()) {
			if (!lastGuilty.equalsIgnoreCase(e.getGuiltyParty())) {
				counter = 1;
				lastGuilty = e.getGuiltyParty();
				sb.append("<h4>Guilty Party: " + e.getGuiltyParty() + "</h4>");
			}
			sb.append(tableHeader);
		
			sb.append("<tr style=\"background-color: #28288A; color: white; padding: 2px;\">");
				sb.append("<td rowspan=\"4\" style=\"vertical-align: top;\">");
				sb.append(counter);
				sb.append("</td>");
				
				sb.append("<td width=\"50%\">Class</td>");
				sb.append("<td width=\"10%\">Start Line</td>");
				sb.append("<td width=\"10%\">End Line</td>");
				sb.append("<td width=\"30%\">Rule</td>");
			sb.append("</tr>");
			
			sb.append("<tr style=\"background-color: #C4DFF5; padding: 2px;\">");
				
				sb.append("<td>");
				sb.append(e.getFullClass());
				sb.append("</td>");
				
				sb.append("<td>");
				sb.append(e.getStartLine());
				sb.append("</td>");
				
				sb.append("<td>");
				sb.append(e.getEndLine());
				sb.append("</td>");
				
				sb.append("<td>");
				sb.append(e.getViolation());
				sb.append("</td>");
			sb.append("</tr>");
			
			sb.append("<tr style=\"background-color: #E4EDF5; padding: 2px;\">");
				sb.append("<td colspan=\"4\">");
				sb.append(e.getMessage());
				sb.append("</td>");
			sb.append("</tr>");
			
			sb.append("<tr>");
				sb.append("<td colspan=\"4\">");
				sb.append(e.getCode());
				sb.append("</td>");
			sb.append("</tr>");
			
			sb.append(tableFooter);
			counter++;
		}
		
		sb.append("<h3>The leading violator as of " + dateOfReport + " is " + sr.getMostGuilty() + "</h3>");
		
		return sb.toString();
	}
	
}
