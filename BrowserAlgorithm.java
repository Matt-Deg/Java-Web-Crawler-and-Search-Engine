/**
 * BrowserAlgorithm class implements algorithms for searching and ranking documents using an inverted index,
 * TF-IDF rankings, and cosine similarities.
 */

package searchEngine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bson.Document;

import com.mongodb.MongoException;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;

class BrowserAlgorithm {
	
	// MongoDB collections for crawled documents and the inverted index
	MongoCollection<Document> crawledDocs;
	MongoCollection<Document> invertedIndex;
	
	private long indexSize; // number of terms in the inverted index
	private mongoConnect mongo; //a mongoConnect class to operate on a MongoDB database
	
	
	/**
     * Constructor for BrowserAlgorithm class
     * Gets the crawled documents collection and the inverted index collection from the MongoDB database
     * @param crawlCollect the name of the collection storing crawled documents
     * @param indexCollect the name of the collection storing the inverted index
     * @param DB_URI the URI for connecting to the MongoDB database
     * @param DB_NAME the name of the MongoDB database
     */
    public BrowserAlgorithm(String crawlCollect, String indexCollect, String DB_URI, String DB_NAME) {
 
    	try {
    		mongo = new mongoConnect(DB_URI, DB_NAME);
        	MongoDatabase browserDB = mongo.getDB();
        	crawledDocs = browserDB.getCollection(crawlCollect);
            invertedIndex = browserDB.getCollection(indexCollect);
            indexSize = invertedIndex.countDocuments();
    	} catch (MongoException e){
    		GUI.showErrorScreen(1);
    	} catch (NullPointerException e) {
    		GUI.showErrorScreen(1);
    	}
    }
    
    /**
     * Get the MongoDB connect object
     * @return the mongoConnect object representing the MongoDB connection
     */
    public mongoConnect getDB () {
    	return mongo;
    }
    
    /**
     * Search for documents using the provided query and browser algorithm method calls
     * @param query the search query
     * @return a LinkedHashMap containing URLs and titles of the top ranked documents
     */
    public LinkedHashMap<String, String> search(String query) {
    	 
    	//preprocesses the query and calculates each query term's TF
    	HashMap<String, Double> queryWords = Crawler.cleanQuery(query);
   
    	// Remove keys not present in the inverted index
    	try {
    		ArrayList<String> distinctValues = invertedIndex.distinct("Term", String.class).into(new ArrayList<>());
            queryWords.keySet().retainAll(distinctValues);
    	} catch (MongoException e) {
    		GUI.showErrorScreen(1);
    	}

        Set<String> queryToPass = queryWords.keySet();

       //algorithm calls
        HashMap<String, Double> searchIDF = calcSearchIDF(queryToPass);
        HashMap<String, Double[]> docWeights = docTF_IDF(queryWords, searchIDF);
        double queryVectorLength = queryVector(queryWords, searchIDF);
        HashMap<String, Double> cosines = cosineSimilarities(docWeights, queryVectorLength);
        ArrayList<String> top25 = pageSort(cosines);
        LinkedHashMap<String, String> urls = getURLs(top25);
    	
        return urls;
    }
    
    
    /**
     * Calculate the IDF (Inverse Document Frequency) for each term in the query
     * @param queryWords a set of terms from the query
     * @return a HashMap containing the IDF values for each term
     */
    private HashMap<String, Double> calcSearchIDF(Set<String> queryWords) {
        HashMap<String, Double> searchIDF = new HashMap<>();

        for (String term : queryWords) {
        	Document cd = invertedIndex.find(Filters.eq("Term", term)).first(); //inverted index document for a specific query term
        	if(cd != null) {
        		org.bson.Document docInfoTuple = (Document) cd.get("Index"); //returns the index value of the document
            	int numDocs = docInfoTuple.size(); //gets the number of documents where the term is present
                double idf = Math.log10(indexSize / numDocs); //calculates idf
                searchIDF.put(term, idf);
        	}
        }

        return searchIDF;
    }

    /**
     * Calculate TF-IDF (Term Frequency * Inverse Document Frequency) weights for documents
     * @param queryWords a map of query terms and their frequencies
     * @param searchIDF a map of query terms and their IDF values
     * @return a HashMap containing key value pairs:
     * the key is a document id
     * the value is an array of three items:
     * [0] the dot product between each document and the query (sum of products of each term's document and query TF-IDF)
     * [1] the vector length for each document (to be converted to Euclidean distance in cosineSimilarities())
     */
    private HashMap<String, Double[]> docTF_IDF(HashMap<String, Double> queryWords, HashMap<String, Double> searchIDF) {
        HashMap<String, Double[]> docWeights = new HashMap<>();
        
        //for every term in the query
        for (Map.Entry<String, Double> term : queryWords.entrySet()) {
            String termKey = term.getKey(); //the term
            double queryTermTFIDF = term.getValue() * searchIDF.getOrDefault(termKey, 0.0); //the TFIDF for the current query term
            
            Document termDoc = invertedIndex.find(Filters.eq("Term", termKey)).first(); //gets the inverted index doc for the current query term
            if (termDoc != null) {
                Document wordDocs = termDoc.get("Index", Document.class); //gets the nested document in the index for the term

                //for every document where the current query term is present, add to that document's weight info
                for (Map.Entry<String, Object> crawledDoc : wordDocs.entrySet()) {
                    String docId = crawledDoc.getKey();
                    double wordFreq = ((Number) crawledDoc.getValue()).doubleValue();
                    Document cd = crawledDocs.find(Filters.eq("ID", docId)).first();
                    if (cd != null) {
                        double maxFreq = cd.getDouble("MaxFrequency");
                        double tf = wordFreq / maxFreq;
                        double docTFIDF = tf * searchIDF.getOrDefault(termKey, 0.0);

                        Double[] weights = docWeights.getOrDefault(docId, new Double[]{0.0, 0.0});
                        weights[0] += docTFIDF * queryTermTFIDF; // Numerator of cosine similarity
                        weights[1] += docTFIDF * docTFIDF; // length of document vector (for denominator)
                        docWeights.put(docId, weights);
                    }
                }
            }
        }
        return docWeights;
    }
    
    /**
     * Calculate the length of the query vector for the cosine similarity calculations
     * @param searchIDF a map of query terms and their IDF values
     * @return a double representing the length of the query vector
     */
   private double queryVector (HashMap<String, Double> queryWords, HashMap<String, Double> searchIDF) {
    	
    	double queryVectorLen = 0; //overall sum of vector length
    	
    	for(Map.Entry<String, Double> term : queryWords.entrySet()) {
    		String termKey = term.getKey(); //the current term
            double length = (term.getValue() * searchIDF.getOrDefault(termKey, 0.0)); //add the term's TFIDF to the vector length
            queryVectorLen += length * length; //add squared length to vector length
    	}
    	
    	return queryVectorLen;
    }
   
   
    /**
     * Calculate cosine similarity between documents and the search query
     * @param weights a map of document IDs and corresponding TF-IDF weights needed for cosine calculation
     * @param queryVectorLength a double representing the length of the query vector
     * (See the above method's javadoc comments for more information)
     * @return a HashMap containing cosine similarity (value) scores for documents (key)
     */
    private HashMap<String, Double> cosineSimilarities(HashMap<String, Double[]> weights, double queryVectorLength){
    	
    	HashMap<String, Double> cosines = new HashMap<>();
    	double queryEuclidean = Math.sqrt(queryVectorLength); //Euclidean distance of query
    	
    	//for each document where at least one query word was found,
    	//calculate its cosine similarity
    	for (Map.Entry<String, Double[]> doc : weights.entrySet()) {
    		
        	String doc_id = doc.getKey();
        	
        	Double[] calcs = weights.get(doc_id);
        	
        	double docSumsSqrt = Math.sqrt(calcs[1]); //Euclidean distance of document
        	double denominator = docSumsSqrt * queryEuclidean; //calculate denominator by multiplying euclidean distances
        	
        	if(denominator != 0) {
        		double cosSim = calcs[0] / denominator;

            	cosines.put(doc_id, cosSim);
        	}
        }
    	
    	return cosines;
    	
    }
    
    /**
     * Perform page ranking based on cosine similarity scores
     * @param cosines a map of document IDs and their cosine similarity scores
     * @return an ArrayList containing document IDs of the top-ranked documents
     */
    private ArrayList<String> pageSort (HashMap<String, Double> cosines){
    	
    	// Create a list of entries
        List<Map.Entry<String, Double>> entries = new ArrayList<>(cosines.entrySet());

        // Sort the entries by values in descending order
        entries.sort((entry1, entry2) -> Double.compare(entry2.getValue(), entry1.getValue()));

        // Get the top 25 values
        List<Map.Entry<String, Double>> topEntries = entries.subList(0, Math.min(25, entries.size()));

        // Convert to ArrayList of keys
        ArrayList<String> topValues = new ArrayList<>();
        for (Map.Entry<String, Double> entry : topEntries) {
            topValues.add(entry.getKey());
        }

        return topValues;

    }
    
    /**
     * Retrieve URLs and titles of the top-ranked documents
     * @param top25 an ArrayList containing document IDs of the top-ranked documents
     * @return a LinkedHashMap containing URLs and titles of the top-ranked documents in-order
     */
    private LinkedHashMap<String, String> getURLs(ArrayList<String> top25) {
        LinkedHashMap<String, String> urls = new LinkedHashMap<>();

        for (String docId : top25) {
            Document cd = crawledDocs.find(Filters.eq("ID", docId)).first();

            // Check if the document exists and contains the "URL" field
            if (cd != null && cd.containsKey("URL")) {
                String url = cd.getString("URL");
                String title = cd.getString("Title");
                urls.put(url, title);
            }
        }

        return urls;
    }
    
    
    
    
}
