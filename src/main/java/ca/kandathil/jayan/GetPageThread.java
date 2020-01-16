package ca.kandathil.jayan;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpClient.Redirect;
import java.net.http.HttpClient.Version;
import java.net.http.HttpResponse.BodyHandlers;
import java.security.NoSuchAlgorithmException;
import java.text.DecimalFormat;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

public final class GetPageThread implements Runnable
{

	private volatile boolean isDone = false;
	private final String URL_LIST;
	private final int NUM_REPEAT;
	private final String USER_ID;
	private final String PASSWORD;
	private final boolean AUTHENTICATE;
	private final String SCHEME;
	private final String HOST;
	private final int PORT;
	private final Map<String,String> DOMAINS;

	// Accept arguments via the constructor
	public GetPageThread(final String pagelist, final int iterations, final String scheme, final String host, final int port, final String user, final String password, final boolean blnAuthenticate, final Map<String,String> hashmapDomains)
	{

		synchronized(this) // Ensure thread safety
		{
			this.URL_LIST = pagelist;
			this.NUM_REPEAT = iterations;
			this.SCHEME= scheme;
			this.HOST= host;
			this.PORT = port;
			this.USER_ID = user;
			this.PASSWORD = password;
			this.AUTHENTICATE = blnAuthenticate;
			this.DOMAINS = hashmapDomains;
		}

	}

	public void run()
	{

		// Round off to a single decimal
		DecimalFormat df = new DecimalFormat("#.#");

		UsernamePasswordAuthenticator authenticator = new UsernamePasswordAuthenticator(this.USER_ID, this.PASSWORD);
		CookieHandler.setDefault(new CookieManager());

		HttpClient httpClient = null;
		try
		{
			httpClient = HttpClient.newBuilder()
							.version(Version.HTTP_2)
							.followRedirects(Redirect.NORMAL)  // With the Redirect.NORMAL mode the HTTP client automatically follows redirects, except HTTPS to HTTP. The client automatically sends another request if he receives a redirect response
								.connectTimeout(Duration.ofMillis(500))
								.authenticator(authenticator)
								.sslContext(SSLContext.getDefault())
								.sslParameters(new SSLParameters())
								.cookieHandler(CookieHandler.getDefault())
								.build();
		}
		catch (NoSuchAlgorithmException e)
		{
			e.printStackTrace();
			System.exit(1);
		}

		System.out.println("Starting GetCQPages...");
		String lineSeparator = System.getProperty( "line.separator" );

		while (!this.isDone)
		{

			long lngStart = 0;
			long lngEnd = 0;
			double dblElapsedTime = 0;

			try
			{
				
				final String THREAD_NAME = Thread.currentThread().getName();
				final String OUTPUT_LOG = "C:\\TEMP\\ContentGulper-" + THREAD_NAME + ".csv";

				// Get time stamp
				lngStart = System.currentTimeMillis();
				
				// ***************
				// Read page list
				// ***************
				
				// Open log in append mode
				BufferedWriter buffWriter;
				try
				{
					buffWriter = new BufferedWriter(new FileWriter(OUTPUT_LOG,true));

					String strCSVColumns = "\"Thread ID\",\"Requested URI\",\"Bytes\",\"Elapsed Milliseconds\",\"HTTP Response Code\"";
					buffWriter.append(strCSVColumns);
					buffWriter.newLine();
					buffWriter.flush();
				}
				catch (IOException e)
				{
					e.printStackTrace();
					this.isDone = true;
		 			return;
		 		}

	 			int intRequest = 0;
		 		int intLineNumber = 0;

	 			// Iterate n times
	 			for(int i=1; i<=this.NUM_REPEAT;i++)
	 			{

					FileInputStream fileInputStream = null;
					DataInputStream dataInputStream = null;
					BufferedReader bufferedReader = null;

					try
					{
						fileInputStream = new FileInputStream(this.URL_LIST);
						dataInputStream = new DataInputStream(fileInputStream);
						bufferedReader = new BufferedReader(new InputStreamReader(dataInputStream));
					}
					catch (FileNotFoundException e)
					{
						System.out.println("[" + THREAD_NAME + "]" + "Error opening URL List file");
						e.printStackTrace();
						this.isDone = true;
						try
						{
							buffWriter.close();
						}
						catch (IOException ioe)
						{
							ioe.printStackTrace();
						}
			 			return;
					}

	 				String strLine = "";
					int intStatus = 0;

					// Go through the input file line by line, starting from the top
					try
					{

						while ((strLine = bufferedReader.readLine()) != null)
						{

							intRequest++;
							intLineNumber++;
							System.out.println(lineSeparator + "[" + THREAD_NAME + "] URI [" + intLineNumber + "]" + lineSeparator);
							long lngContentLength = 0;

							// ******************
							// Retrieve the Page
							// ******************

							long lngStart2;
							long lngEnd2;
							double dblRetrievalTime;

							// Get time stamp
							lngStart2 = System.nanoTime();

							StringBuilder sbURL = new StringBuilder();

							sbURL.append(this.SCHEME + "://");
							sbURL.append(this.HOST);
							sbURL.append(":");
							sbURL.append(this.PORT);
							String strPrefix = sbURL.toString();
							String strURL = strPrefix + strLine;
							System.out.println("[" + THREAD_NAME + "] " + strURL);

							HttpRequest httpRequest = HttpRequest.newBuilder()
								.uri(URI.create(strURL))
								.GET()
								.timeout(Duration.ofSeconds(3))
								.header("Accept-Language", "en-US")
								.setHeader("User-Agent", "Adobe Managed Services load test tool - Content Gulper")
								.build();

							System.out.println("[" + THREAD_NAME + "]" + " Fetching main page...");
							Document jsoupDocument = null;

							// If the URL is for PDF/SVG/XML documents, JSoup HTML Parser should be told not to parse it
							if ( (strURL.endsWith(".pdf")) || (strURL.endsWith(".svg")) || (strURL.endsWith(".xml")) || (strURL.endsWith(".json")) || (strURL.endsWith(".js")) )
							{
								HttpRequest httpRequestFile = HttpRequest.newBuilder()
									.uri(URI.create(strURL))
									.GET()
									.timeout(Duration.ofSeconds(1))
									.header("Accept-Language", "en-US")
									.setHeader("User-Agent", "Adobe Managed Services load test tool - Content Gulper")
									.build();

								// Download the file but discard it
								HttpResponse<Void> httpResponseFile = httpClient.send(httpRequestFile, BodyHandlers.discarding());

								HttpHeaders responseHeaders = httpResponseFile.headers();
								OptionalLong optlongContentLength = responseHeaders.firstValueAsLong("content-length");

								String strLogEntry = ("\"[" + THREAD_NAME + "]\",\"" + strURL + "\",\"" + optlongContentLength.getAsLong() + "\",\"" + df.format(dblElapsedTime) + "\",\"" + httpResponseFile.statusCode() + "\"");

								buffWriter.append(strLogEntry);
								buffWriter.newLine();
								buffWriter.flush();
								buffWriter.close();

							}
							else // If the URL is for an HTML file, download and parse it
							{

								try
								{
									HttpResponse<String> response = httpClient.send(httpRequest, BodyHandlers.ofString());
									HttpHeaders responseHeaders = response.headers();
									jsoupDocument = Jsoup.parse(response.body());
									OptionalLong optlongContentLength = responseHeaders.firstValueAsLong("content-length");
									intStatus = response.statusCode();

									if (optlongContentLength.isPresent())
									{
										try
										{
											lngContentLength = optlongContentLength.getAsLong();
										}
										catch(Exception e)
										{
											System.out.println("Exception retrieving size (content-length in bytes) of the web page...");
											e.printStackTrace();
										}
									}
								}
								catch (Exception e)
								{
									System.out.println("[" + THREAD_NAME + "]" + " Problem URL [" + strURL + "]");
									e.printStackTrace();
								}

								System.out.println("[" + THREAD_NAME + "]" + " Page fetched.");

								Elements jsoupElementsImgScr = null;
								Elements jsoupElementsImports = null;

								System.out.println("[" + THREAD_NAME + "]" + " Parsing page...");

								if (jsoupDocument != null)
								{
									jsoupElementsImgScr = jsoupDocument.select("[src]");
									jsoupElementsImports = jsoupDocument.select("link[href]");

									System.out.println(lineSeparator + "[" + THREAD_NAME + "] Number of links on this page is " + jsoupElementsImgScr.size() + lineSeparator);

									String strLinkHref = "";
									boolean isUrlComplete = false;
									boolean isUrlRelative = false;

									for (Element jsoupElement : jsoupElementsImgScr)
									{

										// ******
										// Images
										// ******

										if (jsoupElement.tagName().equals("img"))
										{

											strLinkHref = jsoupElement.attr("src");
											System.out.println("[" + THREAD_NAME + "]" + " linkhref=[" + strLinkHref + "]");

											if (!(strLinkHref.equalsIgnoreCase("")))
											{
												try
												{
													// Check for relative URLs without a protocol and scheme
													isUrlRelative = UrlRelative(strLinkHref);
													if(isUrlRelative)
													{
														// Strip one of the two forward slashes and add prefix
														strLinkHref = "https:" + strLinkHref;
														isUrlComplete = true;
													}
													else
													{
														isUrlComplete = UrlComplete(strLinkHref);
													}

													// ***************************************
													// Get page assets using a separate thread
													// ***************************************

													System.out.println("[" + THREAD_NAME + "] Invoking GetPageAssetThread() with (Image) URI [" + strLinkHref + "]");
													Runnable r = new GetPageAssetThread(httpClient, this.HOST, strPrefix, strLinkHref, isUrlComplete, OUTPUT_LOG, this.DOMAINS);
													Thread javaThread = new Thread(r);
													javaThread.start();
													//threadpoolGetPageAsset.submit(new GetPageAsset(httpclient, this.SCHEME, this.HOST, this.PORT, this.USER_ID, this.PASSWORD, linkHref, isUrlComplete, OUTPUT_LOG));

												}
												catch (java.lang.StringIndexOutOfBoundsException e)
												{
													System.out.println("StringIndexOutOfBoundsException : [" + THREAD_NAME + "]" + " linkhref=[" + strLinkHref + "]");
												}

											}
										}

										// **********
										// Javascript
										// **********

										strLinkHref = "";
										isUrlComplete = false;

										if (jsoupElement.tagName().equals("script"))
										{

											strLinkHref = jsoupElement.attr("src");

											if (strLinkHref != "")
											{

												// Check for relative URLs without a protocol and scheme
												isUrlRelative = UrlRelative(strLinkHref);
												if(isUrlRelative)
												{
													// Strip one of the two forward slashes and add prefix
													strLinkHref = "https:" + strLinkHref;
													isUrlComplete = true;
												}
												else
												{
													isUrlComplete = UrlComplete(strLinkHref);
												}

												// Get page assets using a separate thread
												System.out.println("[" + THREAD_NAME + "] Invoking GetPageAssetsThread() with (Javascript) URL [" + strLinkHref + "]");
												Runnable r = new GetPageAssetThread(httpClient, this.HOST, strPrefix, strLinkHref, isUrlComplete, OUTPUT_LOG, this.DOMAINS);
												Thread thr = new Thread(r);
												thr.start();
												//threadpoolGetPageAsset.submit(new GetPageAsset(httpclient, this.SCHEME, this.HOST, this.PORT, this.USER_ID, this.PASSWORD, linkHref, isUrlComplete, OUTPUT_LOG));
											}
										}
									}

									System.out.println(lineSeparator + "[" + THREAD_NAME + "] Number of links on this page is " + jsoupElementsImports.size() + lineSeparator);

									strLinkHref = "";
									isUrlComplete = false;

									for (Element src : jsoupElementsImports)
									{
										// *******
										// Imports
										// *******

										if (src.tagName().equals("link"))
										{

											if (src.attr("rel").equalsIgnoreCase("stylesheet") || src.attr("type").equalsIgnoreCase("icon") || src.attr("type").equalsIgnoreCase("preload") || src.attr("type").equalsIgnoreCase("prefetch") || src.attr("type").equalsIgnoreCase("search") || src.attr("type").equalsIgnoreCase("license"))
											{
												strLinkHref = src.attr("href");

												// Check for relative URLs without a protocol and scheme
												isUrlRelative = UrlRelative(strLinkHref);
												if(isUrlRelative)
												{
													// Strip one of the two forward slashes and add prefix
													strLinkHref = "https:" + strLinkHref;
													isUrlComplete = true;
												}
												else
												{
													isUrlComplete = UrlComplete(strLinkHref);
												}

												// Get page assets using a separate thread
												System.out.println("[" + THREAD_NAME + "] Invoking GetPageAssetsThread() with URL [" + strLinkHref + "]");
												Runnable r = new GetPageAssetThread(httpClient, this.HOST, strPrefix, strLinkHref, isUrlComplete, OUTPUT_LOG, this.DOMAINS);
												Thread thr = new Thread(r);
												thr.start();
												//threadpoolGetPageAsset.submit(new GetPageAsset(httpclient, this.SCHEME, this.HOST, this.PORT, this.USER_ID, this.PASSWORD, linkHref, isUrlComplete, OUTPUT_LOG));
											}
										}
									}
								}
							}

							System.out.println("[" + THREAD_NAME + "]" + " Page parsed.");

							// Get time stamp
							lngEnd2 = System.nanoTime();
							dblRetrievalTime = ((double)(lngEnd2-lngStart2))/1000000;
							//String strLine2 = "[" + THREAD_NAME + "]," + strURL + "," + df.format(dblRetrievalTime) + ",milliseconds," + intContentLength + ",bytes";
							String strLine2 = "\"[" + THREAD_NAME + "]\",\"" + strURL + "\",\"" + lngContentLength + "\",\"" + df.format(dblRetrievalTime) + "\",\"" + intStatus + "\"";
							System.out.println(strLine2);
							buffWriter.append(strLine2);
							buffWriter.newLine();
							buffWriter.flush();

							}// end while loop

							this.isDone = true;

						}
						catch (IOException | InterruptedException e)
						{
							e.printStackTrace();
				 			this.isDone = true;
						} 

						intLineNumber = 0;

						try
						{
							fileInputStream.close();
							dataInputStream.close();
					 		bufferedReader.close();
						}
						catch (IOException e)
						{
							e.printStackTrace();
				 			this.isDone = true;
						}
				 		   
				 		System.out.println(lineSeparator + "[" + THREAD_NAME + "] Iteration " + i + " completed." + lineSeparator);

		 			} // end for loop
	 			
					// Get time stamp
					lngEnd = System.currentTimeMillis();
					dblElapsedTime = ((double)(lngEnd-lngStart))/1000;

					String strLine3 = lineSeparator + "[" + THREAD_NAME + "] Retrieved ["+ intRequest + "] pages - took [" + df.format(dblElapsedTime) + "] seconds total." + lineSeparator;

					try
					{
						buffWriter.append(strLine3);
						buffWriter.newLine();
						buffWriter.flush();
						buffWriter.close();
					}
					catch (IOException e)
					{
						e.printStackTrace();
			 			this.isDone = true;
					}

						System.out.println(strLine3);

				}

			finally
			{
				// Nothing to do
			}
		}

	}

	private boolean UrlComplete(final String strURL)
	{
		if( strURL.substring(0, 4).equalsIgnoreCase(this.SCHEME) || strURL.substring(0, 5).equalsIgnoreCase(this.SCHEME) )
		{
			return true;
		}
		else
		{
			return false;
		}
	}

	private boolean UrlRelative(final String strURL)
	{
		if( strURL.substring(0, 2).equalsIgnoreCase("//") )
		{
			return true;
		}
		else
		{
			return false;
		}
	}

}
