package edu.cornell.cs.osmot.cache;

import java.io.IOException;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;

import edu.cornell.cs.osmot.options.Options;
import edu.cornell.cs.osmot.logger.Logger;

/**
 * This class implements a cache the search engine uses to generate snippets.
 * 
 * Each document must have a unique identifier. We derive a filename from the
 * unique identifier. We use unique identifiers of the form: blah.xyz/1234567
 * This gets translated into a path for the form: cacheDir/blah/1234/1234567.txt
 * 
 * @author Filip Radlinski
 * @author Vladimir Menkov (minor mods, 2011)
 * @version 1.0, April 2005
 */
public class Cache {

    private int maxLength;

    private String cacheDirectory;
    private String extension = ".txt";

    /** sets the extension added to all file names in this cache */
    public void setExtension(String e) {
	extension = e;
    }

	/**
	 * Create a cache with the specified cache directory. The default length
	 * returned is set to DEFAULT_LENGTH.
	 * 
	 * @param cacheDir
	 *            The cache directory.
	 */
	public Cache(String cacheDir) throws IOException {
	    this(cacheDir, Options.getInt("CACHE_DEFAULT_LENGTH"));
	}

	/**
	 * Create a cache with the specified cache directory and default length.
	 * 
	 * @param cacheDir
	 *            The cache directory.
	 * @param defLength
	 *            The maximum number of bytes returned for any document, by
	 *            default.
	 */
	public Cache(String cacheDir, int defLength) {

		this.cacheDirectory = cacheDir;
		this.maxLength = defLength;

		// Make the cache directory if it doesn't exist;
		File f = new File(cacheDir);
		if (f.mkdirs()) {
			log("CACHE: Created cache directory: " + cacheDir);
		}

		log("Created a Cache.");
	}

	/**
	 * Returns up to the first maxLength bytes given this unique identifier
	 * 
	 * @param uniqId
	 *            Identifier of the document to retrieve.
	 * @return The first maxLength bytes of the document.
	 */
	public String getContents(String uniqId) {

		return getContents(uniqId, maxLength);
	}

	/**
	 * Returns up to the first <length>bytes given this unique identifier
	 * 
	 * @param uniqId
	 *            Identifier of the document to retrieve.
	 * @param length
	 *            Length to retrieve.
	 * @return The first <length>bytes of the document.
	 */
	public String getContents(String uniqId, int length) {

		FileReader in;
		char contents[] = new char[length];

		String filename = getFilename(uniqId);

		// Get the contents
		try {
			in = new FileReader(filename);
			in.read(contents, 0, length);
			in.close();
		} catch (Exception E) {
			log("CACHE:Document file " + filename + " missing.");
			return null;
		}

		return new String(contents);
	}

    /** Is there a cache file for this id?
     */
    public boolean fileExists(String uniqId) {
	String filename = getFilename(uniqId);
	return (new File(filename)).exists();
    }

	/**
	 * Insert a given document into the cache.
	 * 
	 * @param uniqId
	 *            The unique identifier of the document.
	 * @param document
	 *            The document contents to cache.
	 * @return True on success, false on failure.
	 */
    /*
	public boolean cacheDocument(String uniqId, String document) {
	    cacheFile(uniqId, document, ".txt");
	}
	public boolean cacheMetadata(String uniqId, String document) {
	    cacheFile(uniqId, document, ".xml");
	}
    */
    	public boolean cacheDocument(String uniqId, String document) {

		// Make sure all the directories exist, if not, make them
		String dirs = getFilename(uniqId);
		dirs = dirs.substring(0, dirs.lastIndexOf('/'));
		File f = new File(dirs);

		// If the directories exist, this does nothing.
		if (f.mkdirs()) {
			log("CACHE: Created directories in path: " + dirs);
		}

		try {
			FileWriter fw = new FileWriter(getFilename(uniqId), false);
			fw.write(document);
			fw.close();
		} catch (Exception e) {
			log("CACHE: Error caching document " + uniqId + ": " + e.toString());
			return false;
		}
		return true;
	}

	/**
	 * Delete a document from the cache.
	 * 
	 * @param uniqId
	 *            The indentifier of the document
	 * @return True on success, false on failure.
	 */
	public boolean deleteDocument(String uniqId) {

		File f = new File(getFilename(uniqId));

		if (f.canWrite()) {
			if (f.delete())
				return true;
		}

		return false;
	}

	/**
	 * Return the filename the document with unique identifier uniqId is stored
	 * if <dir>is the root of the cache. This is static so it allows the
	 * filename to be found without actually creating a cache object.
	 * 
	 * @param uniqId
	 *            The unique identifier of the document.
	 * @param rootDir
	 *            The root directory of the cache.
	 * @return The filename this document would be stored in.

	 @deprecated
	 */
	public static String getFilename(String uniqId, String rootDir) {
	    return getFilename( uniqId,  rootDir, ".txt");
	}

    public static String getFilename(String uniqId, String rootDir, String ext) {

		int l = uniqId.length();
		String filename;

		if (l > 3 && uniqId.indexOf("/") != -1) {

			// The usual filename code we use
			filename = rootDir + "/" + uniqId.substring(0, l - 3) + "/"
					+ uniqId.substring(uniqId.indexOf("/") + 1, l);

			// A dot followed by stuff in the type should be discarded
			// (e.g. cs.th/1234567 => cs/1234567)
			filename = filename.replaceFirst("\\.[A-Z]+/", "/");

			filename = filename + ext;

		} else if (l > 3) {
			
			// New arXiv ids have no slashes, just YYMM.nnnn			
			filename = rootDir + "/" + uniqId.replace('.','/')+ext;
			
		} else {

			// In case a user wants to use simple short ids, especially when
			// first installing this code
			// and experimenting with it.
			filename = rootDir + "/" + uniqId;
		}

		return filename;
	}

	/**
	 * Return the filename this document is/would be stored in.
	 * 
	 * @param uniqId
	 *            The unique identifier of the document.
	 * @return The filename this document is/would be stored in.
	 */
	public String getFilename(String uniqId) {

	    return getFilename(uniqId, cacheDirectory, extension);
	}

	/**
	 * Log something to the general log.
	 * 
	 * @param s
	 *            The string to log.
	 */
	private static void log(String s) {

		Logger.log(s);
	}
}
