/*
 * Interface: UserInteractions
 * Description: This interface defines methods for handling user interactions with a search engine.
 */

package searchEngine;

import java.util.LinkedHashMap;

interface UserInteractions {
	
	/**
	 * If the user selects to run a crawler, then create a connection to the database and initialize a crawling service
	 * @param link The database URI input from the user
	 * @param dbName The name of the database where crawled data will be stored, taken from user input
	 */
	void crawlSelected(String link, String dbName);
	
	/**
	 * When a crawler has reached its crawling depth, close down the crawling service
	 */
	void crawlFinished();
	
	/**
	 * Create a new instance of a BrowserAlgorithm and open up Browser GUI
	 * @param link The database URI input from the user
	 * @param dbName The name of the database where crawled data will be stored, taken from user input
	 */
	void callBrowser(String link, String dbName);
	
	/**
	 * Returns the top search results for a user query
	 * @param query User search query in String form
	 * @return A Linked Hashmap storing key(url) and value(web page title) pairs
	 */
	LinkedHashMap<String, String> searchBrowser(String query);
	
	/**
	 * Cleans up system resources upon graceful user exit from the program
	 */
	void termination();
	
	/**
	 * Cleans up system resources upon unexpected user or program exit from the program
	 */
	void unexpectedTermination();
}