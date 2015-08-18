package gov.sbs.plugins;

import gov.sbs.plugins.domain.Pmd;
import gov.sbs.plugins.domain.Pmd.File.Violation;
import gov.sbs.plugins.domain.Report;
import gov.sbs.plugins.domain.Report.Subreport;
import gov.sbs.plugins.domain.Report.Subreport.Entry;
import gov.sbs.plugins.domain.ReportType;
import gov.sbs.plugins.domain.ViewGenerator;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactoryConfigurationError;

import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.TaskAction;

public class VanderBlameTask extends DefaultTask {
    public File pmdXmlReport;
    public File projectRoot;
    public File outputHtmlReport;
    public File outputXmlReport;

    @TaskAction
    public void vanderBlameTask() throws IOException, JAXBException, IllegalArgumentException, TransformerException, TransformerFactoryConfigurationError, DatatypeConfigurationException {
        System.out.println("Going to now read in the PMD report located at ::: " + pmdXmlReport);

        Pmd results = readReport();
        
        String projectRootPath = projectRoot.getAbsolutePath() + "/";
        
        for (Pmd.File file : results.getFile()) {
        	runGitBlame(projectRootPath, file.getName().replace(projectRootPath, ""), file);
        }
        
        //Uncomment this for testing purposes only
        //results.getFile().get(0).getViolation().get(1).setGuiltyParty("ericsond");
        
        generateReport( results );
    }
    
    public Pmd readReport() throws IOException, JAXBException {
		if (!pmdXmlReport.exists()) {
			System.out.println("Could not find the PMD XML file located at "+pmdXmlReport+".  Did you run PMD first or pass in the correct path?");
			System.exit(0);
		}
		
		FileInputStream fis = new FileInputStream(pmdXmlReport);
		byte[] data = new byte[(int) pmdXmlReport.length()];
		fis.read(data);
		fis.close();

		String xml = new String(data, "UTF-8");
    	
		return (Pmd)JAXBContext.newInstance(Pmd.class).createUnmarshaller().unmarshal(new StringReader(xml));
    }
    
	private void runGitBlame(String projectRootPath, String filePathAndName, Pmd.File file) {

		//String nameRegex = Pattern.quote(" (") + "(.*?)" + Pattern.quote(" ");
		//Pattern namePattern = Pattern.compile(nameRegex);
		//Pattern linePattern = Pattern.compile("\\s(\\w+)$");
		
		String os = System.getProperty("os.name").toLowerCase();

		ProcessBuilder newBuilder;
		if (os.contains("windows")) {
			newBuilder = new ProcessBuilder( "cmd" );
		} else {
			newBuilder = new ProcessBuilder( "/bin/bash" );
		}
		//init shell
        
        Process newP=null;
        try {
            newP = newBuilder.start();
        }
        catch (IOException e) {
            System.out.println(e);
        }
		
        BufferedWriter p_stdin = new BufferedWriter(new OutputStreamWriter(newP.getOutputStream()));
        
        try {
            //Execution
		    p_stdin.write("cd " + projectRootPath);
		    p_stdin.newLine();
		    p_stdin.flush();
		    
		    p_stdin.write("git blame " + filePathAndName);
		    p_stdin.newLine();
		    p_stdin.flush();
        }
        catch (IOException e) {
        	System.out.println(e);
        }

        //Close the shell by execution exit command
        try {
            p_stdin.write("exit");
            p_stdin.newLine();
            p_stdin.flush();
        }
        catch (IOException e) {
            System.out.println(e);
        }

	    // write stdout of shell (=output of all commands)
	    Scanner s = new Scanner( newP.getInputStream() );

	    Map<Integer, String> linedFile = new HashMap<Integer, String>();
	    
	    while (s.hasNextLine()) {
	    	String line = s.nextLine();
	    	
			String[] guiltyParty = line.split("\\(", 2);
			String[] tempArr = guiltyParty[1].split(" ");
			String name = tempArr[0];
	    	
			String[] lineOfCode = line.split("\\)", 2);
			tempArr = lineOfCode[0].split(" ");
			Integer lineNum = new Integer(tempArr[tempArr.length-1]);
			
			linedFile.put(lineNum, lineOfCode[1]);
	    	
			for (Violation v : file.getViolation()) {
				if (v.getBeginline() == lineNum.intValue()) {
					v.setGuiltyParty(name);
					break;
				}
			}
	    }
	    
	    //Now add the lines of code that were flagged into the issue
		for (Violation v : file.getViolation()) {
			if (v.getBeginline() == v.getEndline()) {
				v.setCode(linedFile.get(new Integer(v.getBeginline())));
			} else {
				StringBuffer sb = new StringBuffer();
				int counter = v.getEndline() - v.getBeginline();
				for (int x=0; x <= counter; x++) {
					sb.append(linedFile.get(v.getBeginline()+x));
					if (x != counter) {
						sb.append("<br />");
					}
				}
				v.setCode(sb.toString());
			}
		}

	    s.close();
	}
	
	private void generateReport(Pmd results) throws JAXBException, IOException, IllegalArgumentException, TransformerException, TransformerFactoryConfigurationError, DatatypeConfigurationException {
		
		//First do the PMD report
		Report masterReport = new Report();
		GregorianCalendar gCalendar = new GregorianCalendar();
		gCalendar.setTime(new Date());
		masterReport.setTimestamp(DatatypeFactory.newInstance().newXMLGregorianCalendar(gCalendar));
		
		Subreport pmdReport = new Subreport();
		pmdReport.setName(ReportType.PMD);
		masterReport.getSubreport().add(pmdReport);
		
		for (Pmd.File file : results.getFile()) {
			for (Violation v : file.getViolation()) {
				Entry entry = new Entry();
				
				entry.setGuiltyParty(v.getGuiltyParty());
				entry.setFullClass(v.getPackage() + "." + v.getClazz());
				entry.setStartLine(v.getBeginline());
				entry.setEndLine(v.getEndline());
				entry.setStartColumn(v.getBegincolumn());
				entry.setEndColumn(v.getEndcolumn());
				entry.setViolation(v.getRule());
				entry.setViolationRuleSet(v.getRuleset());
				entry.setCode(v.getCode());
				entry.setMessage(v.getValue());
				pmdReport.getEntry().add(entry);
			}
		}
		
		//Now do the find bugs report
		//For now we do nothing
		Subreport findBugsReport = new Subreport();
		findBugsReport.setName(ReportType.FIND_BUGS);
		masterReport.getSubreport().add(findBugsReport);
		
		generateReport(masterReport);

	}
	
	private void generateReport(Report blameReport) throws IllegalArgumentException, TransformerException, TransformerFactoryConfigurationError, JAXBException, IOException {
        
        //The XML output
        //XML representation of the report
		JAXBContext context = JAXBContext.newInstance(Report.class);
        Marshaller m = context.createMarshaller();
        StringWriter sw = new StringWriter();
        //for pretty-print XML in JAXB
        m.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
        m.marshal(blameReport, sw);
        
        //The XML output
        outputXmlReport.getParentFile().mkdirs();
		if (!outputXmlReport.exists()) {
			outputXmlReport.createNewFile();
		}
        
		OutputStream xmlFile=null;
		try {
			xmlFile=new FileOutputStream(outputXmlReport);
			xmlFile.write(sw.toString().getBytes());
		} finally {
			if (null != xmlFile) {
				xmlFile.close();
			}
		}
        
		
		
        //The HTML output
		//HTML Report Template
		System.out.println("\n\n\nSTARTIN");

		//ClassLoader cl = getClass().getClassLoader();
		//InputStream is = cl.getResourceAsStream("templates/report/VanderblameReport.html");
		//String template = readFile(is);
		//System.out.println(template);
		
	    
        
        outputHtmlReport.getParentFile().mkdirs();
		if (!outputHtmlReport.exists()) {
			outputHtmlReport.createNewFile();
		}
        
		OutputStream htmlFile=null;
		try {
			htmlFile=new FileOutputStream(outputHtmlReport);
			
			htmlFile.write(ViewGenerator.createReport(blameReport).getBytes());
		} finally {
			if (null != htmlFile) {
				htmlFile.close();
			}
		}
        
	}
	
	/*
	private String readFile(InputStream in) throws IOException {
		System.out.println(" - 1");
		InputStreamReader is = new InputStreamReader(in);
		System.out.println(" - 2");
		StringBuilder sb=new StringBuilder();
		BufferedReader br = new BufferedReader(is);
		System.out.println(" - 3");
		String read = br.readLine();
		System.out.println(" - 4");
		while(read != null) {
		    //System.out.println(read);
		    sb.append(read);
		    System.out.println(" - 5");
		    read =br.readLine();
		    System.out.println(" - 6");

		}
		System.out.println(" - 7");
		return sb.toString();
	}
	*/
	
	/*
	private static String output(InputStream inputStream) throws IOException {
		StringBuilder sb = new StringBuilder();
		BufferedReader br = null;
		try {
			br = new BufferedReader(new InputStreamReader(inputStream));
			String line = null;
			while ((line = br.readLine()) != null) {
				sb.append(line + System.getProperty("line.separator"));
			}
		} finally {
			br.close();
		}
		return sb.toString();
	}
	*/
}
