/**
 * Search Engine class that serves as the intermediary between database connection services,
 * crawling services, GUI control, and the algorithm for search results
 */

package searchEngine;

import java.util.LinkedHashMap;

import javax.swing.SwingUtilities;

import org.bson.Document;
import com.mongodb.client.MongoCollection;

//implements the UserInteractions interface
public class SearchEngine implements UserInteractions {
	
	private static final String START_URL = "https://www.muhlenberg.edu/";  //where to begin crawling
	private static final String CrawlerInventoryCollectionName = "CrawlerDocs"; //MongoDB collection where crawled docs are stored
	private static final String InvertedIndexCollection = "InvertedIndex"; //MongoDB collection where inverted index docs are stored
	
	private static String dbURI; //MongoDB connection URI - input from user in GUI
	private static String dbNAME; //MongoDB database to connect to - input from user in GUI
	
	private GUI gui; //a GUI object for the search engine
	private static mongoConnect mongoDB; //to be used to connect to MongoDB when crawler is used
	private static Crawler crawler; //a Crawler object for the search engine
	private BrowserAlgorithm browser; //a BrowserAlgorithm object so search engine can access the search algorithm
	
	private boolean crawlerUsed = false; //if crawler is used, signal to callBrowser() that dbURI and dbNAME still require initialization

	/**
	 * Main method that creates and initializes a search engine object
	 * When search engine initialized, GUI constructs and brings up welcome screen
	 */
    public static void main(String[] args) {
    	SearchEngine se = new SearchEngine();
        se.initialize();
    }
    
    /**
     * initializes the gui object, which boots up the GUI
     */
    private void initialize() {
        gui = new GUI(this);
    }
    
    /**
     * Connects to user database using their inputed information
     */
    private void connectDB() {
    	mongoDB = new mongoConnect(dbURI, dbNAME);
    }
    
    
    /**
     * Implemented Interface Methods
     */
    
    /**
     * Displays a waiting screen while a crawler runs
     * Connect and setup MongoDB database for this project
     * Initialize crawler and boot up crawling service
     * @param link The database URI input from the user
	 * @param dbName The name of the database where crawled data will be stored, taken from user input
     */
    public void crawlSelected (String link, String dbName) {
    	
    	//Waiting screen
    	gui.showWaitingScreen();
    	
    	//MongoDB database
    	dbURI = link;
    	dbNAME = dbName;
    	connectDB();
    	MongoCollection<Document> crawlBase = mongoDB.createCollection(CrawlerInventoryCollectionName);
    	MongoCollection<Document> invertedIndex = mongoDB.createCollection(InvertedIndexCollection);
    	
    	//Crawler service
    	crawler = new Crawler(this, crawlBase, invertedIndex);
    	crawler.addShutdownHook(); //ensures crawler resources are shutdown gracefully upon any exit from program
    	crawler.crawl(START_URL);
    }
    
    /**
     * Closes the crawling waiting screen
     * Starts off browser duties when crawler threads have all cleared up
     */
    public void crawlFinished() {
        crawlerUsed = true;
        gui.closeWaitingScreen();
        SwingUtilities.invokeLater(() -> callBrowser(dbURI, dbNAME));
    }
    
    
    /**
     * Handles MongoDB resources to ready the environment for the BrowserAlgorithm browser
     * Initializes the browser
     * Opens browser GUI display
     * @param link The database URI input from the user
	 * @param dbName The name of the database where crawled data will be stored, taken from user input
     */
    public void callBrowser(String link, String dbName) {
    	
    	//closes crawler connection if it existed
    	if(mongoDB != null) {
    		mongoDB.closeConnection();
    	}
    	
    	//initialize class variables if not done already through crawler
    	if(!crawlerUsed) {
    		dbURI = link;
        	dbNAME = dbName;
    	}
    	browser = new BrowserAlgorithm(CrawlerInventoryCollectionName, InvertedIndexCollection, dbURI, dbNAME);
    	gui.browserScreen();
    }
    
    /**
     * Calls the browser to search for results based on a given query
     * This method is called from the browserScreen() in the GUI every time the user clicks the search button
     * @param query User query string inputed from GUI
     * @return a HashMap holding the top 25 urls with their corresponding titles
     */
    public LinkedHashMap<String, String> searchBrowser(String query) {
    	return browser.search(query);
    }
    
    /**
     * Gracefully closes resources when user exits the Browser Screen GUI display
     */
    public void termination() {
    	mongoConnect browserDB = browser.getDB();
    	browserDB.closeConnection();
    }
    
    /**
     * Gracefully closes resources when the user or program unexpectedly terminates the program
     * Called when the stopwords file is not in the directory or the waiting screen is manually closed while
     * crawling services are ongoing
     */
    public void unexpectedTermination() {
    	mongoDB.closeConnection();
    	crawler.shutdown();
    }
}
