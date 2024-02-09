/**
 * This class implements the graphical user interface (GUI) for the Search Engine application,
 * providing functionalities for user interaction such as initiating search operations,
 * displaying search results, and handling errors.
 */

package searchEngine;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import java.util.LinkedHashMap;
import java.util.Map;

public class GUI {

    //The UserInteractions object for interacting with the user interface (search engine)
    private UserInteractions uInterface;
    
    //The JFrame for the waiting screen
    private JFrame waitingFrame;

    /**
     * Constructs a GUI object with the specified UserInteractions instance and initializes the welcome screen
     * @param userI The UserInteractions instance for handling user interactions
     */
    public GUI(UserInteractions userI) {
    	uInterface = userI;
	WelcomeFrame();
    }

    /**
     * Initializes the welcome screen GUI components.
     */
     private void WelcomeFrame() {
        JFrame welcomeFrame = new JFrame("Welcome to Search Engine");
        welcomeFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        int screenWidth = (int) screenSize.getWidth();
        int screenHeight = (int) screenSize.getHeight();
        int frameWidth = 600;
        int frameHeight = 300;
        welcomeFrame.setBounds((screenWidth - frameWidth) / 2, (screenHeight - frameHeight) / 2, frameWidth, frameHeight);

        // Textboxes for database link and name
        JTextField databaseLinkField = new JTextField();
        JTextField databaseNameField = new JTextField();
        
        databaseLinkField.setPreferredSize(new Dimension(150, 25));
        databaseNameField.setPreferredSize(new Dimension(150, 25));
        
        JCheckBox crawlerCheckBox = new JCheckBox("Run Crawler");

        JButton startButton = new JButton("Start Search");
        startButton.addActionListener(new ActionListener() {

            /**
             * Invoked when the start button is clicked
             * Initiates either crawling or searching based on user selection
             * @param e The ActionEvent object: "Start Search" JButton
             */
            public void actionPerformed(ActionEvent e) {
            	if(crawlerCheckBox.isSelected()) {
            	    uInterface.crawlSelected(databaseLinkField.getText(), databaseNameField.getText());
            	} else {
            	    uInterface.callBrowser(databaseLinkField.getText(), databaseNameField.getText());
            	}
		    
            	welcomeFrame.dispose();
            }
        });

        JPanel welcomePanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        gbc.anchor = GridBagConstraints.CENTER;
        gbc.insets = new Insets(10, 0, 10, 0); // Add vertical spacing

        welcomePanel.add(new JLabel("Welcome to Search Engine"), gbc);

        gbc.gridy++;
        gbc.gridwidth = 1;
        welcomePanel.add(new JLabel("Database URI:"), gbc);

        gbc.gridx++;
        welcomePanel.add(databaseLinkField, gbc);

        gbc.gridy++;
        gbc.gridx = 0;
        welcomePanel.add(new JLabel("Database Name:"), gbc);

        gbc.gridx++;
        welcomePanel.add(databaseNameField, gbc);

        gbc.gridy++;
        gbc.gridx = 0;
        gbc.gridwidth = 2;
        welcomePanel.add(crawlerCheckBox, gbc);

        gbc.gridy++;
        welcomePanel.add(startButton, gbc);

        welcomeFrame.add(welcomePanel);
        welcomeFrame.setVisible(true);
    }

    /**
     * Displays a waiting screen indicating that crawling is in progress
     */
    void showWaitingScreen() {
    	SwingUtilities.invokeLater(() -> {
          JFrame waitingFrame = new JFrame("Please wait");
          waitingFrame.setSize(300, 100);
          waitingFrame.setLocationRelativeTo(null);
          waitingFrame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
          waitingFrame.addWindowListener(new WindowAdapter() {
        	
              /**
               * Invoked when the waiting screen window is closed. Calls the unexpectedTermination()
               * method of the UserInteractions interface
               * @param e The WindowEvent object: Exit Screen Button
               */
        	  public void windowClosed(WindowEvent e) {
        	      uInterface.unexpectedTermination();
        	  }
          });

          JLabel waitingLabel = new JLabel("             Please wait, crawling in progress...");
          waitingFrame.add(waitingLabel);

          waitingFrame.setVisible(true);
          
    	});
      }

	
    /**
     * Closes the waiting screen.
     */
    public void closeWaitingScreen() {
        if (waitingFrame != null) {
            SwingUtilities.invokeLater(() -> waitingFrame.dispose());
        }
    }
    
    /**
     * Displays a browser screen for searching and displaying search results
     */
    public void browserScreen() {
        JFrame frame = new JFrame("Simple Web Browser");
        frame.setExtendedState(JFrame.MAXIMIZED_BOTH);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        JTextField searchField = new JTextField();
        JButton searchButton = new JButton("Search");
        JLabel resultLabel = new JLabel();
        JTextArea urlTextArea = new JTextArea();

        searchField.setPreferredSize(new Dimension(200, 30));

        /**
         * Add action listener to search button
         * Whenever the search button is clicked, intake the user's text entry and attempt to pass it to the
         * browsing algorithm
         */
        searchButton.addActionListener(e -> {
            String searchText = searchField.getText();
            if (!searchText.isEmpty()) {
                resultLabel.setText("Search Results for: " + searchText);
                LinkedHashMap<String, String> results = uInterface.searchBrowser(searchText);

                // Clear the text area before adding new URLs
                urlTextArea.setText("");

                for (Map.Entry<String, String> result : results.entrySet()) {
                    urlTextArea.append(result.getValue() + ": " + result.getKey() + "\n");
                }
            } else {
                resultLabel.setText("Please input a search query");
            }
        });

        JPanel topPanel = new JPanel();
        topPanel.setLayout(new BorderLayout());
        topPanel.setBackground(Color.GRAY);

        topPanel.add(searchField, BorderLayout.CENTER);
        topPanel.add(searchButton, BorderLayout.EAST);

        JPanel resultPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        resultPanel.add(resultLabel);
        topPanel.add(resultPanel, BorderLayout.SOUTH);

        frame.add(topPanel, BorderLayout.NORTH);
        frame.add(new JScrollPane(urlTextArea), BorderLayout.CENTER);

        /*
         * When the user exits the browser screen, the program gracefully terminates
         */
        frame.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent windowEvent) {
            	uInterface.termination();
            }
        });

        frame.setVisible(true);
    }

	
    /*
     * GUI Error Message that can be called by various parts of the code when an error makes the program unrecoverable
     * @param status Integer informing what error message should be printed out
     */
    public static void showErrorScreen(int status) {
    	
    	 String errorMessage;
    	 
    	 switch(status) {
    	 
    	 case 0:
    		errorMessage = "There was an error connecting to the MongoDB database.\n" +
                     "Please make sure your database is configured properly\n" +
                     "and that your URI and database name are correct.";
    		 break;
         
    	 case 1:
    		 errorMessage = "An error occurred when attempting to connect to the MongoDB database.\n" +
                     "Please make sure your database and your java environment are configured properly.";
    		 break;
    		 
    	 case 2:
    		 errorMessage = "There was an error when creating the MongoDB collection for the crawled documents.\n" +
                     "Please check that your MongoDB is configured correctly.";
    		 break;
         
    	 case 3:
    		 errorMessage = "Please restart the program and input a valid MongoDB link and database name.";
    		 break;
    		 
    	 case 4:
    		 errorMessage = "No stopwords file found for crawler: please place 'stopwords.txt' in your program file folder.";
    		 break;
         
    	 default:
    		 errorMessage = "An error occurred when attempting to connect to the MongoDB database.\n" +
                     "Please make sure your database and your java environment are configured properly.";
    		 break;
    		 
    	 }

         // Create a custom dialog
         JOptionPane optionPane = new JOptionPane(errorMessage, JOptionPane.ERROR_MESSAGE, JOptionPane.DEFAULT_OPTION, null, new Object[]{ "Exit Program" });

         // Create a JDialog and set the JOptionPane as its content pane
         JDialog dialog = optionPane.createDialog("Error");

         // Add a window listener to handle window closing event
         dialog.addWindowListener(new java.awt.event.WindowAdapter() {
             @Override
             public void windowClosing(java.awt.event.WindowEvent windowEvent) {
            	 dialog.dispose();
             }
         });

         // Set the dialog to be modal
         dialog.setModal(true);

         // Make the dialog unresizable
         dialog.setResizable(false);

         // Show the dialog
         dialog.setVisible(true);
    }
}
