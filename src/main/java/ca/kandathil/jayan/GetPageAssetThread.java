package ca.kandathil.jayan;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DecimalFormat;
import java.time.Duration;
import java.util.Map;
import java.util.OptionalLong;

public final class GetPageAssetThread implements Runnable
{

	private volatile boolean isDone = false;
	
	private final HttpClient HTTP_CLIENT;
	private final String PREFIX;
	private final String HOST;
	private final String ASSET_URL;
	private final boolean IS_URL_COMPLETE;
	private final String OUTPUT_LOG;
	private final Map<String,String> DOMAINS;
	
	// Accept arguments via the constructor
	public GetPageAssetThread(final HttpClient httpClient, final String strHost, final String strPrefix, final String elementURL, final boolean isUrlComplete, final String strOutputLog, final Map<String,String> hashmapDomains)
	{
		synchronized(this) // Ensure thread safety
		{
			this.HTTP_CLIENT = httpClient;
			this.PREFIX = strPrefix;
			this.HOST = strHost;
			this.ASSET_URL = elementURL;
			this.IS_URL_COMPLETE = isUrlComplete;
			this.OUTPUT_LOG = strOutputLog;
			this.DOMAINS = hashmapDomains;
		}
	}

	@Override
	public void run()
	{

		long lngStart = 0;
		long lngEnd = 0;
		double dblElapsedTime = 0.0;

		final String THREAD_NAME = Thread.currentThread().getName();

		while (!this.isDone)
		{

			final String strThreadName = Thread.currentThread().getName();
			//System.out.println("GetPageAssets [" + strThreadName + "]");
			
			// Open log in append mode
			try(BufferedWriter buffWriter = new BufferedWriter(new FileWriter(this.OUTPUT_LOG,true)))
	 		{
				
				String strFinalAssetURL = "";

				if (this.IS_URL_COMPLETE)
				{
					strFinalAssetURL = this.ASSET_URL;
					System.out.println("[" + strThreadName + "] " + "URL is complete [" + this.ASSET_URL + "]");
				}
				else
				{
					System.out.println("[" + THREAD_NAME + "] URL is incomplete " + this.ASSET_URL);
					strFinalAssetURL = this.PREFIX + this.ASSET_URL;
				}

				String strDomainStatus = DomainStatus(strFinalAssetURL);

				// Proceed only if the domain is not set to Ignore

				if(!(strDomainStatus.contentEquals("Ignore")))
				{
					lngStart = System.nanoTime();
					String strResponse = requestAsset(this.HTTP_CLIENT, strFinalAssetURL, this.OUTPUT_LOG);
					lngEnd = System.nanoTime();

					this.isDone = true;
					int intCommaIndex = strResponse.indexOf(",");

					dblElapsedTime = ((double)(lngEnd-lngStart))/1000000;
					DecimalFormat df = new DecimalFormat("#.##");
					String strLine = ("\"[" + strThreadName + "]\",\"" + strFinalAssetURL + "\",\"" + strResponse.substring(intCommaIndex + strResponse.length()) + "\",\"" + df.format(dblElapsedTime) + "\",\"" + strResponse.substring(0, intCommaIndex) + "\"");

					buffWriter.append(strLine);
					buffWriter.newLine();
					buffWriter.flush();
					buffWriter.close();
				}

	 		}
			catch (IOException | InterruptedException e)
			{
				System.out.println("[" + strThreadName + "Error creating output log [" + this.OUTPUT_LOG + "]");
				System.out.print(e.toString());
				this.isDone = true;
				return;
			}
		}
	}

	private String requestAsset(HttpClient httpClient, final String strURL, final String strOutputLog) throws IOException, InterruptedException
	{

		HttpRequest httpRequestFile = HttpRequest.newBuilder()
					.uri(URI.create(strURL))
					.GET()
					.timeout(Duration.ofSeconds(3))
					.header("Accept-Language", "en-US")
					.setHeader("User-Agent", "Adobe Managed Services load test tool - Content Gulper")
					.build();

		// Download the file but discard it
		HttpResponse<Void> httpResponseFile = httpClient.send(httpRequestFile, BodyHandlers.discarding());

		HttpHeaders responseHeaders = httpResponseFile.headers();
		String strStatusCode = Integer.toString(httpResponseFile.statusCode());
		String strContentLength = "";

		try
		{
			OptionalLong optlongContentLength = responseHeaders.firstValueAsLong("content-length");
			if (optlongContentLength.isPresent())
			{
				strContentLength = Long.toString(optlongContentLength.getAsLong());
			}
		}
		catch(Exception e)
		{
			System.out.println("Exception retrieving size (content-length in bytes) of the web page...");
			e.printStackTrace();
		}
		
		return strStatusCode + "," + strContentLength;

	}

	// ****************************************************************
	// Avoid invoking un-wanted external entities listed in domains.txt
	// ****************************************************************

	private String DomainStatus(final String strURL)
	{
		
		final String THREAD_NAME = Thread.currentThread().getName();
		
		String strReturnValue = "";
		
		for (Map.Entry<String, String> hashmapEntry : this.DOMAINS.entrySet())
		{

			String strKey = hashmapEntry.getKey();
			String strValue = hashmapEntry.getValue();

			//System.out.println("[" + strThreadName + "]" + " Iterating though domain list [" + strKey + " | " + strValue + "]" + intCounter++);
			
			int intBegin = strURL.indexOf("//");
			int intEnd = strURL.indexOf("/", intBegin + 3);

			//System.out.println(intBegin);
			//System.out.println(intEnd);

			String strDomain = strURL.substring(intBegin + 2,intEnd);
			String strDomainListEntry = strKey;

			//System.out.println("[" + strThreadName + "]" + " Domain in URL is [" + strDomain + "]");
			//System.out.println("[" + strThreadName + "]" + " Domain in list is [" + strDomainListEntry + "]");

			int intCompare = strDomainListEntry.compareToIgnoreCase(strDomain);
			//System.out.println("[" + strThreadName + "] " + "intCompare = " + intCompare);

			// If the domain in the URL matches an entry in the no-invoke list
			if (intCompare == 0)
			{

				// If the domain is listed as no-invoke
				if(strValue.equalsIgnoreCase("0"))
				{
					System.out.println("[" + THREAD_NAME + "]" + " Domain listed as not to be invoked [" + strDomainListEntry + "]");
					System.out.println("[" + THREAD_NAME + "]" + " Deliberately ignoring URL [" + strURL + "]");
					this.isDone = true;
					strReturnValue = "Ignore";
				}
				// If the domain is listed as replace
				else if (strValue.equalsIgnoreCase("1"))
				{
					System.out.println("[" + THREAD_NAME + "]" + " Domain listed as to be replaced [" + strDomainListEntry + "]");
					String strUpdatedURI = strURL.replace(strDomain, this.HOST);
					System.out.println("[" + THREAD_NAME + "] " + " [" + strDomain + "] replaced with [" + this.HOST + "]");
					System.out.println("[" + THREAD_NAME + "] " + " URL = [" + strUpdatedURI + "]");
					strReturnValue = strUpdatedURI;
				}
			}
		}

		return strReturnValue;
	}

}
