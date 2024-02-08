/**
 * This class establishes a connection to a MongoDB database and provides methods
 * for interacting with it, such as creating collections and closing the connection.
 */

package searchEngine;

import org.bson.Document;

import com.mongodb.MongoClient;
import com.mongodb.MongoClientURI;
import com.mongodb.MongoException;
import com.mongodb.MongoWriteException;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;

class mongoConnect {
	
	private static String DB_URI ; //The URI for connecting to the MongoDB database
	private static String DB_NAME; //The name of the MongoDB database

    private MongoClient client; //The MongoClient object for connecting to the MongoDB server
    private MongoDatabase db; //The MongoDatabase object representing the connected database
    
    /**
     * Constructs a new mongoConnect object with the specified database URI
     * and database name, and establishes a connection to the MongoDB server
     * @param link The URI for connecting to the MongoDB database
     * @param dbName The name of the MongoDB database
     */
    public mongoConnect(String link, String dbName) {
    	
    	DB_URI = link;
    	
    	DB_NAME = dbName;
    	
    	try {
    		
    	     MongoClientURI uri = new MongoClientURI(DB_URI);
    	     client = new MongoClient(uri);
    	     db = client.getDatabase(DB_NAME);
    	
    	} catch (MongoException e) {
    	     GUI.showErrorScreen(0);
    	} catch (IllegalArgumentException e) {
    		GUI.showErrorScreen(3);
    	} catch (Exception e) {
    		GUI.showErrorScreen(1);
    	}
    }
    
    /**
     * Retrieves the connected MongoDatabase object
     * @return The MongoDatabase object representing the connected database.
     */
    public MongoDatabase getDB() {
        return db;
    }
    
    /**
     * Closes the connection to the MongoDB server if it exists
     */
    public void closeConnection() {
        if (client != null) {
            client.close();
        }
    }
    
    /**
     * Creates a new collection in the connected database with the specified name
     * and returns the corresponding MongoCollection object
     * @param collectionName The name of the collection to be created
     * @return The MongoCollection object representing the newly created collection
     *         or null if an error occurred during creation.
     */
    MongoCollection<Document> createCollection(String collectionName) {
    	try {
    	     db.createCollection(collectionName);
    	     return db.getCollection(collectionName);
    	} catch (MongoWriteException e) {
    		GUI.showErrorScreen(2);
    		return null;
    	}
    }
    
}

