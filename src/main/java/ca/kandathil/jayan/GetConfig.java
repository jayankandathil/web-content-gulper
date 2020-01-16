package ca.kandathil.jayan;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Author : Jayan Kandathil
 * Date : April 15, 2013
 * version : 0.3
 */

public final class GetConfig
{

	public static String main(final String strProperty, final String strSettingsFile) throws IOException
	{

		File f = null;
		Properties props = new Properties();

		f = new File(strSettingsFile);
		
		try(InputStream is = new FileInputStream(f);)
		{
			props.load(is);
			is.close();
		}

		return(props.getProperty(strProperty));
	}
}
