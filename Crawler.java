/**
 * This class implements a web crawler that concurrently retrieves web pages, processes their content,
 * and stores relevant information in a MongoDB database. It also provides methods for
 * cleaning query strings and managing the crawler's execution.
 */

package searchEngine;

import org.bson.Document;

import org.jsoup.Jsoup;
import org.jsoup.select.Elements;
import opennlp.tools.stemmer.PorterStemmer;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.mongodb.MongoException;
import com.mongodb.MongoWriteException;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.ArrayList;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

class Crawler {
	
	private org.jsoup.nodes.Document jSoupDoc; //A JSoup Document object representing the web page being processed
	
	 //Document Cleaning Tools
	private static HashSet<String> stopwords = null; //The set of stopwords loaded from file
	private static PorterStemmer porterStemmer = null; //The PorterStemmer object for stemming words
	
	//Crawler tools
	private static final int CRAWL_LIMIT = 5000; //The maximum number of web pages to crawl
	private static final int THREAD_POOL_SIZE = 4; //The number of threads in the thread pool
	private int threadOpenCount = THREAD_POOL_SIZE; //The count of open threads in the thread pool
	private ExecutorService executorService; //The ExecutorService for managing thread execution
	private AtomicInteger CRAWLED = new AtomicInteger(0); //The count of crawled web pages

	 private HashSet<String> visitedLinks = new HashSet<>(); //The set of visited links to prevent revisiting
	 
	 private static UserInteractions uInterface; //The user interface for displaying messages and interactions
	 
	 private MongoCollection<org.bson.Document> crawlBase; //The MongoDB collection for storing crawled documents
	 private MongoCollection<org.bson.Document> invertedIndex; //The MongoDB collection for storing the inverted index

	 
	 /**
	  * Constructs a new Crawler object with the specified user interface,
	  * crawl collection, and inverted index collection
	  * @param userI The user interface for displaying messages and interactions (in this program, passed as a SearchEngine instance)
	  * @param crawl The MongoDB collection for storing crawled documents
	  * @param index The MongoDB collection for storing the inverted index
	  */
	 public Crawler(UserInteractions userI, MongoCollection<org.bson.Document> crawl, MongoCollection<org.bson.Document> index) {
	        executorService = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
	        loadStopwords();
	        porterStemmer = new PorterStemmer();
	        uInterface = userI;
	        crawlBase = crawl;
	        invertedIndex = index;
	 }
	 
	 /**
      * Loads stopwords from file into a HashSet.
      */
     private static void loadStopwords () {
		 
		 try {
			 stopwords = new HashSet<>((Files.readAllLines(Paths.get("stopwords.txt"))));
		 } catch (IOException e) {
			 GUI.showErrorScreen(5);
			 uInterface.unexpectedTermination();
		 }
	 }
	 
     /**
	  * Cleans a user input query by removing stopwords and stemming words
	  * @param query The query string to be cleaned
	  * @return A HashMap containing cleaned query terms and their normalized frequencies (TFs)
	  */
	 public static HashMap<String, Double> cleanQuery(String query){
		 
		 if(stopwords == null) {
			 loadStopwords();
		 }
		 if(porterStemmer == null) {
			 porterStemmer = new PorterStemmer();
		 }
		 
		 //clean the query
		 String query_scraped = (query.toLowerCase()).replaceAll("[^a-zA-Z0-9\\s]", " ");
	     String[] query_array = query_scraped.split("\\s+");
	     ArrayList<String> queryList = new ArrayList<>(Arrays.asList(query_array));
	     queryList.removeAll(stopwords);
	     queryList.replaceAll(word -> porterStemmer.stem(word));
	     
	     //calculate query frequencies
	     int total_query_length = queryList.size();
	     HashMap<String, Double> queryWords = new HashMap<>();
	      for (String word : queryList) {
	           queryWords.put(word, queryWords.getOrDefault(word, 0.0) + 1);
	       }
	       
	      //calculate query term frequencies (TFs)
	      for (Map.Entry<String, Double> entry : queryWords.entrySet()) {
	    	    String word = entry.getKey();
	    	    double frequency = entry.getValue();

	    	    double normalizedFrequency = frequency / total_query_length;

	    	    queryWords.put(word, normalizedFrequency);
	       } 
	      return queryWords; 
	 }
	 
	 
	 /**
	  * Initiates crawling starting from the given URL, and keeps recursively crawling URLs until crawl depth is reached
	  * @param url The URL to crawl
	  */
	 public void crawl(String url) {
		 
		    /**
		     * Shutdown each thread's executor service once crawl depth is reached
		     * Once all threads have successfully closed, signal the search engine that the crawler has finished,
		     * and return from the method call
		     */
		    if (CRAWLED.get() >= CRAWL_LIMIT) {
		    	//synchronized to ensure each executor service is terminated correctly and that the
		    	//threadOpenCount is decremented correctly
		    	synchronized (this) {
		            executorService.shutdown();
		            if (!executorService.isTerminated()) {
		                try {
		                    executorService.awaitTermination(10, TimeUnit.SECONDS);
		                } catch (InterruptedException e) {
		                    executorService.shutdownNow();
		                    Thread.currentThread().interrupt();
		                }
		            }
		            threadOpenCount -= 1;
		            if (threadOpenCount == 0) {
		                uInterface.crawlFinished();
		            }
		        }
		        return;
		    }

		    /**
		     * Clean and store the current url to prevent crawling over duplicates
		     */
		    String strippedURL = storeCleanedLinks(url);
		    if (visitedLinks.contains(strippedURL)) {
		        return;
		    }
		    visitedLinks.add(strippedURL);

		    /**
		     * Submits a task for execution to the ExecutorService
		     * The task retrieves the content of the web page specified by the URL, processes it, 
		     * and continues crawling by exploring the links found on that page
		     */
		    executorService.submit(() -> {
		        try {
		            jSoupDoc = Jsoup.connect(url).get(); //attempts to retrieve url
		            processPage(url, jSoupDoc); //processes the url page
		            //ensures the number of crawled documents is incremented correctly
		            synchronized (CRAWLED) {
		                CRAWLED.incrementAndGet();
		            }

		            Elements links = jSoupDoc.select("a[href]"); //gets all the html links in the current url page
		            //calls crawl() on each new link found
		            for (var link : links) {
		                if (cleanLinks(link.attr("abs:href"))) {
		                    String nextLink = link.absUrl("href").split("#")[0];
		                    crawl(nextLink);
		                }
		            }
		        } catch (java.net.SocketTimeoutException e) {
		            //System.out.println("Timeout error: " + e.getMessage());
		        } catch (java.io.IOException e) {
		            //System.out.println("IO error: " + e.getMessage());
		        } catch (Exception e) {
		            //System.out.println("An error occurred: " + e.getMessage());
		        }
		    });
		}

	 /**
		 * Processes the content of a web page and stores the info in the crawler document collection (crawlBase)
		 * Dynamically adds to and updates the inverted index collection (invertedIndex)
		 * @param url The URL of the web page
		 * @param jSoupDoc The JSoup Document object representing the web page
		 */
		private void processPage(String url, org.jsoup.nodes.Document jSoupDoc) {
	    	
	      String id = "" + CRAWLED.get();
	      String title = jSoupDoc.title();
	      
	      //cleans the web page title
	      String cleaned_title = ((jSoupDoc.title()).toLowerCase()).replaceAll("[^a-zA-Z0-9\\s]", " ");
	      String[] title_words = cleaned_title.split("\\s+");
	      ArrayList<String> titleList = new ArrayList<>(Arrays.asList(title_words));
	      titleList.removeAll(stopwords);
	      titleList.replaceAll(word -> porterStemmer.stem(word));
	      
	      //cleans the web page content  
	      String content = ((jSoupDoc.text()).toLowerCase()).replaceAll("[^a-zA-Z0-9\\s]", " ");
	      String[] words = content.split("\\s+");
	      ArrayList<String> wordList = new ArrayList<>(Arrays.asList(words));
	      wordList.removeAll(stopwords);
	      wordList.replaceAll(word -> porterStemmer.stem(word));
	     
	      //calculates frequencies for each word in the web page content
	      HashMap<String, Double> frequencyMap = new HashMap<>();
	       for (String word : wordList) {
	    	   if(word.length() <= 30) {
	    		   frequencyMap.put(word, frequencyMap.getOrDefault(word, 0.0) + 1);
	    	   }
	       }
           
	      //finds the single largest frequency
	       double largestValue = Integer.MIN_VALUE;
	        for (double value : frequencyMap.values()) {
	            if (value > largestValue) {
	                largestValue = value;
	            }
	        }
	        
	        //adds extra frequency weight to title words
	        for (String word : titleList) {
		    	   if(word.length() <= 30) {
		    		   frequencyMap.put(word, frequencyMap.getOrDefault(word, 0.0) + 	largestValue);
		    	   }
		       }
	       
	        //Adds the current web page's info as a document to crawled document collection
	        org.bson.Document mongoDoc = new org.bson.Document()
	        		.append("ID", id)
	        		.append("URL", url)
	        		.append("Title", title)
	        		.append("MaxFrequency", largestValue);
	        
	      try {
	    	  crawlBase.insertOne(mongoDoc);
	      } catch (MongoWriteException e) {
	    	  //System.out.println("The following website failed to be inserted into the crawler documents: " + url);
	      }

	      //Updates the inverted index for each unique term found in the web page
	      org.bson.Document docInfoTuple = null;
	      for (Map.Entry<String, Double> entry : frequencyMap.entrySet()) {
	    	  
	          String term = entry.getKey();
	          double frequency = entry.getValue();
	          
	          try {
	             Document cd = invertedIndex.find(Filters.eq("Term", term)).first();
	          
	             //if the term is already indexed, append the current doc's id and term frequency to the existing term document
	             if (cd != null) {
	        	     docInfoTuple = (Document) cd.get("Index");
	                 if (docInfoTuple != null) {
	                     docInfoTuple.append(id, frequency);
	                  
	                     invertedIndex.updateOne(Filters.eq("Term", term), new Document("$set", new Document("Index",  docInfoTuple)));
	                 } else {
	                     System.out.println("Error finding word in inverted index: null access to word Document<doc_id, word_frequency>");
	                 }
	             //if the term is not indexed, create a new document for it and store it in the invertedIndex collection    
	             } else {

	                 docInfoTuple = new org.bson.Document();
	                 docInfoTuple.append(id, frequency);
	                 // Append this document to the indexDoc under the word key
	                 org.bson.Document indexDoc = new org.bson.Document()
	                         .append("Term", term)
	                         .append("Index", docInfoTuple);
	                 invertedIndex.insertOne(indexDoc);
	             }
	         } catch (MongoWriteException e){
	        	//System.out.println("The following term failed to be inserted or updated into the inverted index: " + term);
	         } catch (MongoException e) {
	        	//System.out.println("The following term failed to be inserted or updated into the inverted index: " + term);
	         }
	      }   
	    }
	    
		/**
		 * Removes the protocol and "www" prefix from the given URL to clean it for storage
		 * @param link The URL to be cleaned
		 * @return The cleaned URL without the protocol and "www" prefix
		 */
	    private String storeCleanedLinks (String link) {
	    	String cleaned = link.replaceFirst("https://www\\.|http://www\\.|https://|http://", "");
	    	return cleaned;
	    }

	    /**
	     * Checks if the provided link is suitable for crawling based on specified conditions
	     * @param link The URL to be checked
	     * @return True if the link meets the criteria for crawling, false otherwise
	     */
        private boolean cleanLinks(String link) {
	    	if ((link.toLowerCase().contains("muhlenberg.edu")) && (!link.toLowerCase().contains("keyword"))) {
	    		return true;
	    	}
	    	else {
	    		return false;
	    	}
	    }

        /**
         * Method that initiates the shutdown of the ExecutorService manually
         */
        public void shutdown() {
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(10, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
                Thread.currentThread().interrupt(); // Preserve the interrupted status
            }
        }
        
        /**
         * Adds a shutdown hook to the JVM runtime, which executes the shutdown() method when the JVM is shutting down
         */
        public void addShutdownHook() {
            Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown));
        }
    
}

