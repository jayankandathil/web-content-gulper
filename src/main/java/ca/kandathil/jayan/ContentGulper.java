/**
 * Author : Jayan Kandathil
 * Date : November 14, 2019
 * version : 0.2
 */

package ca.kandathil.jayan;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;

import ca.kandathil.jayan.GetConfig;

public final class ContentGulper
{

	private static final String strSettingsFile = "config.txt";

	static String URL_LIST = "";
	static int NUM_REPEAT = 1;
	static int NUM_CONCURRENT_USERS = 1;
	static boolean AUTHENTICATE = false;
	static String USER_ID = "";
	static String PASSWORD = "";
	static String SCHEME = "";
	static String HOST = "";
	static int PORT = 443;
	
	public static void main(String[] args) throws IOException
	{

		// **********************
		// Load the configuration
		// **********************

		System.out.println("\nLoading configuration from config.txt\n");

		try
		{
			URL_LIST = GetConfig.main("URL_LIST", strSettingsFile);
			NUM_REPEAT = Integer.parseInt(GetConfig.main("NUM_REPEAT", strSettingsFile));
			NUM_CONCURRENT_USERS = Integer.parseInt(GetConfig.main("NUM_CONCURRENT_USERS", strSettingsFile));
			AUTHENTICATE = Boolean.parseBoolean(GetConfig.main("AUTHENTICATE", strSettingsFile));
			USER_ID = GetConfig.main("USER_ID", strSettingsFile);
			PASSWORD = GetConfig.main("PASSWORD", strSettingsFile);
			SCHEME = GetConfig.main("SCHEME", strSettingsFile);
			HOST = GetConfig.main("HOST", strSettingsFile);
			PORT = Integer.parseInt(GetConfig.main("PORT", strSettingsFile));
			
		}
		catch (NumberFormatException | IOException e)
		{
			e.printStackTrace();
			System.exit(1); // No point continuing if we can't get the test configuration
		}


		// ************************
		// Target Domain Adjustment
		// ************************

		Map<String, String> hashmapDomains = new HashMap<String, String>();
		FileInputStream fileDomInputStream;
		DataInputStream dataDomInputStream;
		BufferedReader bufferedDomReader;

		try
		{
			fileDomInputStream = new FileInputStream("domains.txt");
			dataDomInputStream = new DataInputStream(fileDomInputStream);
			bufferedDomReader = new BufferedReader(new InputStreamReader(dataDomInputStream));
		}
		catch (FileNotFoundException e)
		{
			System.out.println("Error opening domain list file");
			e.printStackTrace();
 			return;
		}

		String strDomLine = "";
		String strDomain = "";
		String strDomainAction = "";

		System.out.println();
		System.out.println("Reading domain exclusion list...");
		System.out.println();

		while ((strDomLine = bufferedDomReader.readLine()) != null)
		{
			
			int j = 1;
			StringTokenizer st = new StringTokenizer(strDomLine, "|"); 

			int i = 0;
			String[] strArray = new String[2];

			while(st.hasMoreTokens())
			{ 
				strArray[i] = st.nextToken();
				//System.out.println("Token " + i + " = " + strArray[i]);
				i++;
			}

			strDomain = strArray[0];
			strDomainAction = strArray[1];
			hashmapDomains.put(strDomain,strDomainAction);

			System.out.println("[" + j + "] " + strDomain + "," + strDomainAction);
			System.out.println("Domain hashmap size is [" + hashmapDomains.size() + "] items");
			j++;

		}

		bufferedDomReader.close();
		dataDomInputStream.close();
		fileDomInputStream.close();

		System.out.println();
		System.out.println("Done.");
		System.out.println();

		// *****************
		// Spawn new threads
		// *****************
		
		Runnable r = new GetPageThread(URL_LIST, NUM_REPEAT, SCHEME, HOST, PORT, USER_ID, PASSWORD, AUTHENTICATE, hashmapDomains);

		for(int i=0; i<NUM_CONCURRENT_USERS; i++)
		{
			Thread javaThread = new Thread(r);
			javaThread.start();
		}

		System.out.println("Kicked off [" + NUM_CONCURRENT_USERS + "] concurrent user(s) repeating the URL list [" + NUM_REPEAT + "] time(s)");

		return;

	}

}
