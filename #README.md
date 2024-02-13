# Java-Web-Crawler-and-Search-Engine
This repository contains the java code for a web crawler and search engine project. It crawls over muhlenberg.edu web pages, and then allows the user to enter queries to search for the most relevant pages in the domain. Here is how it works:

The user interacts with the search engine through a GUI created with Java's Swing API.
The first screen allows the user to input the name and URI of their MongoDB database, and select whether or not they want the crawler to run.
If the crawler is selected, then a crawling algorithm crawls the muhlenberg.edu domain by accessing and scraping URLs from each webpage.
Each webpage's basic information (title, url, maximum word frequency, and unique ID) is stored as a document in a MongoDB collection.
Each word in the webpage's HTML content is used to create and update an inverted index, which is also stored through documents in a different MongoDB collection.
Once the crawler is finished running (or after the user submits their MongoDB information without selecting the crawler), a new GUI screen appears that allows the user
to search for a query. The results from this query are determined by cosine similarity between the crawled documents and the user query.
This cosine similarity is calculated through TF-IDF calculations. The user can search as many queries as they want before exiting the search engine GUI, which
closes all resources and ends the program.
