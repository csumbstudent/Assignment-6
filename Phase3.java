/**
 * Created by Chris on 10/11/2015.
 */
import javax.swing.*;
import java.awt.*;
import javax.swing.border.*;
import java.awt.event.*;
import java.io.File;
import java.lang.Comparable;
import javax.swing.JOptionPane;
class CardStack{
   Card[] cards;
   int numCards;
   CardStack(int maxCards){
      cards = new Card[maxCards];
      numCards = 0;
   }
   public boolean addCard(Card card){
      if(numCards == cards.length)
         return false;
      cards[numCards] = card;
      numCards++;
      return true;
   }
   public Card inspectTopCard(){
      if(numCards == 0)
         return new Card('M', Card.Suit.spades);
      return new Card(cards[numCards - 1]);
   }
}
public class Phase3 {
   static Controller controller;
   static Model model;
   static View view;
   public static final int NUM_CARDS_PER_HAND = 7;
   public static final int NUM_PLAYERS = 2;
   public static void main(String[] args){
      controller = new Controller();
      view = new View("High Card Game", NUM_PLAYERS, NUM_CARDS_PER_HAND);
      Model model = new Model(controller, view);
      model.startGame();

   }
   private static class Model{
      static Runnable timer = new Runnable(){
         public void run(){
            while(timerRunning) {
               String timerString = "";
               timerSecond++;
               if (timerSecond == 60) {
                  timerSecond = 0;
                  timerMinute++;
               }
               timerString = timerMinute + ":";
               if (timerSecond < 10)
                  timerString += "0";
               timerString += timerSecond;
               view.updateTimer(timerString);
               try {
                  Thread.sleep(1000);
               } catch (Exception e) {
               }
            }
         }
      };
      static Thread timerThread;
      static boolean timerRunning = false;
      static View view;
      static Controller controller;
      static int numPacksPerDeck = 1;
      static int numJokersPerPack = 0;
      static int numUnusedCardsPerPack = 0;
      static Card[] unusedCardsPerPack = null;
      //Holds all of the cards that player 1 wins.
      static Card[] player1Winnings = new Card[NUM_CARDS_PER_HAND * 2];
      //Holds all of the cards that player 2 wins.
      static Card[] player2Winnings = new Card[NUM_CARDS_PER_HAND * 2];
      static Card[][] playerWinnings = new Card[NUM_PLAYERS][NUM_CARDS_PER_HAND*2];
      //Contains the point values for each of the cards, in the same order as the
      //valid cards are held in the validValues array of the Card class.
      static int[] cardPointValues = {1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1};
      //A listener to listen for mouse events.
      static Controller listener = new Controller();
      static Card[] playedCards = new Card[NUM_PLAYERS];
      //HUMAN_PLAYER is the index of the human player in this game.
      private static final int HUMAN_PLAYER = 1;
      private static String players[] = {"Computer", "You"};
      private static int timerMinute = 0, timerSecond = 0;
      public static final int NUM_STACKS = 2;
      private static CardStack[] cardStacks = new CardStack[NUM_STACKS];
      private static int selectedCard = -1;
      private static int[] noPlayCount = new int[NUM_PLAYERS];
      private static int consecutiveTurnSkips = 0;
      private static boolean newCardTopsPlacedLastRound = false;
      public Model(Controller controller, View view){
         this.controller = controller;
         this.view = view;
         this.highCardGame = new CardGameFramework(this.numPacksPerDeck, this.numJokersPerPack, this.numUnusedCardsPerPack, this.unusedCardsPerPack, Phase3.NUM_PLAYERS, Phase3.NUM_CARDS_PER_HAND);
      }
      /* In: A Card array representing winnings from either player
      Out: An integer containing the score calculated from the winnings. */
      static int calculateScore(Card[] winnings) {
         int score = 0;
         for (Card card : winnings)
            if (card != null)
               score++;
            else
               break;
         return score;
      }
      private static char[] getValidValues(Card card){
         String cardValues = new String(Card.validCardValues);
         char[] validValues = new char[2];
         //The edge cards (A and Joker) are special cases.
         if(card.getValue() == cardValues.charAt(0)){
            validValues[0] = cardValues.charAt(cardValues.length() - 1);
            validValues[1] = cardValues.charAt(1);
         }
         else if(card.getValue() == cardValues.charAt(cardValues.length() - 1)){
            validValues[0] = cardValues.charAt(cardValues.length() - 2);
            validValues[1] = cardValues.charAt(0);
         }
         else{
            validValues[0] = cardValues.charAt(cardValues.indexOf(card.getValue()) - 1);
            validValues[1] = cardValues.charAt(cardValues.indexOf(card.getValue()) + 1);
         }
         return validValues;
      }
      private static void playComputerCard(){
         //Play the computer's card
         int[] computerPlay = getPossiblePlay(0);
         if(computerPlay[0] != -1){
            int computerStack = computerPlay[0];
            int computerCardPosition = computerPlay[1];
            Card computerCard = highCardGame.getHand(0).inspectCard(computerCardPosition);
            highCardGame.playCard(0, computerCardPosition);
            highCardGame.takeCard(0, NUM_CARDS_PER_HAND - 1);
            view.drawHand(0, highCardGame.getHand(0), false);
            view.setPlayedCard(computerStack, computerCard);
            cardStacks[computerStack].addCard(computerCard);
            view.setStatusText("Computer played " + computerCard + " on Stack " + (computerStack+1) + ".");
            consecutiveTurnSkips = 0;
         }
         else{
            view.setStatusText("Computer could not play a card.");
            noPlayCount[0]++;
            view.updateNoPlayLabel(noPlayCount[0], noPlayCount[1]);
            consecutiveTurnSkips++;
            //The computer does not have a card at this point. Indicate a skipped turn
            //by returning -1.
            if(consecutiveTurnSkips >= 2) {
               if(!placeNewStackTops()){
                  return;
               }
               consecutiveTurnSkips = 0;
            }
         }
         if(newCardTopsPlacedLastRound){
            view.setStatusText(view.getStatusText() + " A new card has been placed on each stack.");
            newCardTopsPlacedLastRound = false;
         }
         if(highCardGame.getHand(0).getNumCards() == 0)
            gameOver();
         view.setStatusText(view.getStatusText() + " Your turn!");
      }
      //returns true if the card was placed.
      public static boolean placeCard(int stack){

         Card topCard = cardStacks[stack].inspectTopCard();
         Card playerCard = highCardGame.getHand(HUMAN_PLAYER).inspectCard(selectedCard);
         String cardValues = new String(Card.validCardValues);
         char[] validValues = getValidValues(cardStacks[stack].inspectTopCard());

         for(char value : validValues){
            if(value == playerCard.getValue()){
               consecutiveTurnSkips = 0;
               cardStacks[stack].addCard(playerCard);
               highCardGame.playCard(HUMAN_PLAYER, selectedCard);
               selectedCard = -1;
               highCardGame.takeCard(HUMAN_PLAYER, NUM_CARDS_PER_HAND - 1);
               highCardGame.getHand(HUMAN_PLAYER).sort();
               view.drawHand(HUMAN_PLAYER, highCardGame.getHand(HUMAN_PLAYER), true);
               view.setPlayedCard(stack, playerCard);
               if(highCardGame.getHand(HUMAN_PLAYER).getNumCards() == 0) {
                  gameOver();
                  return true;
               }
               playComputerCard();
               return true;
            }
         }
         view.setStatusText("That card cannot go there... Select a different card.");
         return false;
      }
      private static int determineWinner(){
         if(noPlayCount[0] == noPlayCount[1])
            return -1;
         else if(noPlayCount[0] > noPlayCount[1])
            return 1;
         else
            return 0;
      }
      private static void skipPlayerTurn(){
         int play[] = getPossiblePlay(HUMAN_PLAYER);
         if(play[0] != -1){
            view.setStatusText("You have a card that can be placed on Stack " + (play[0] + 1) + ".");
            return;
         }
         boolean playComputerCard = true;
         consecutiveTurnSkips++;
         noPlayCount[HUMAN_PLAYER]++;
         view.setStatusText("");
         if(consecutiveTurnSkips == 2) {
            if(!placeNewStackTops()){
               playComputerCard = false;
            }
            consecutiveTurnSkips = 0;
         }
         if(playComputerCard) {
            playComputerCard();
         }
         view.updateNoPlayLabel(noPlayCount[0], noPlayCount[1]);
      }
      //The actual highCardGame.
      static CardGameFramework highCardGame;
      private static void initGame(){
         highCardGame.newGame();
         highCardGame.deal();
         highCardGame.getHand(HUMAN_PLAYER).sort();
         for(int i = 0; i < playerWinnings[0].length; i++){
            playerWinnings[0][i] = null;
            playerWinnings[1][i] = null;
         }
         view.drawHand(0, highCardGame.getHand(0), false);
         view.drawHand(1, highCardGame.getHand(1), true);
         view.setPlayLabelText(0, "Stack 1");
         view.setPlayLabelText(1, "Stack 2");
         view.updateNoPlayLabel(0, 0);
         view.setStatusText("Click on a card in your hand, then click on the card stack you would like to play it on.");
         cardStacks[0] = new CardStack(Deck.MAX_CARDS);
         cardStacks[1] = new CardStack(Deck.MAX_CARDS);
         cardStacks[0].addCard(highCardGame.getCardFromDeck());
         cardStacks[1].addCard(highCardGame.getCardFromDeck());
         view.setPlayedCard(0, cardStacks[0].inspectTopCard());
         view.setPlayedCard(1, cardStacks[1].inspectTopCard());
         view.removeStatusListener();
         timerMinute = 0;
         timerSecond = 0;
         view.updateTimer("0:00");
         noPlayCount[0] = 0;
         noPlayCount[1] = 0;
         //For debugging
         //for(int i = 0; i < 40; i++)
         //   highCardGame.getCardFromDeck();
         timerThread = new Thread(timer);
         view.addButtonListener();
         consecutiveTurnSkips = 0;
         timerThread = new Thread(timer);
         timerRunning = true;
         timerThread.start();
      }
      private static void startGame(){
         initGame();
         view.showCardTable();

      }
      /* In: A Card object representing the card that the human player picked
      Out: An integer representing the position in the computer's hand of the card
           it chooses to play.
      This function makes this game very hard to win. The computer knows which card
      the human chooses to play. It chooses which card to play based on this. */
      static int[] getPossiblePlay(int player) {
         //Look at each of the stacks, and determine if there is a card in
         //its hand that can be placed on the stack.
         for(int i = 0; i < cardStacks.length; i++){
            String cardValues = new String(Card.validCardValues);
            char[] validValues = getValidValues(cardStacks[i].inspectTopCard());
            for(int x = 0; x < highCardGame.getHand(player).getNumCards(); x++){
               Card card = highCardGame.getHand(player).inspectCard(x);
               for(char value : validValues)
                  if(card.getValue() == value)
                     return new int[] {i, x};
            }
         }
         return new int[] {-1, -1};
      }
      private static boolean placeNewStackTops(){
         int errorFlagCount = 0;
         for(int i = 0; i < NUM_STACKS; i++){
            Card card = highCardGame.getCardFromDeck();
            if(!card.errorFlag) {
               cardStacks[i].addCard(card);
               view.setPlayedCard(i, card);
            }
            else
               errorFlagCount++;
         }
         if(errorFlagCount == NUM_STACKS) {
            gameOver();
            return false;
         }
         else {
            newCardTopsPlacedLastRound = true;
         }
         return true;
      }
      private static void gameOver(){
         timerRunning = false;
         int winner = determineWinner();
         if(winner == HUMAN_PLAYER)
            view.setStatusText(view.getStatusText() + " You beat the computer! Good job! Click here to play again!");
         else if(winner == 0)
            view.setStatusText(view.getStatusText() + " The computer beat you... Click here to play again!");
         else
            view.setStatusText(view.getStatusText() + " The game ends in a draw. Click here to play again!");
         view.removeButtonListener();
         view.addStatusListener();
      }
      /* In: A Card object
      Out: An integer representing that card's value */
      static int getCardPointValue(Card card) {
         if (card.errorFlag)
            return -1;
         String values = new String(Card.validCardValues);
         return cardPointValues[values.indexOf(card.getValue())];
      }
      static int determineWinner(Card... playedCards){
         int highestCard = -1;
         for(int i = 0; i < playedCards.length; i++){
            int numHigherThan = 0;
            for(int x = 0; x < playedCards.length; x++){
               if(i != x && playedCards[i].compareTo(playedCards[x]) < 0)
                  numHigherThan++;
            }
            if(numHigherThan == playedCards.length - 1) {
               highestCard = i;
               break;
            }
         }
         return highestCard;
      }



   }
   private static class View{
      static JLabel[][] playerHands;
      static JLabel[] playedCardLabels;
      static JLabel[] playLabelText;
      static JLabel timerLabel = new JLabel("0:00");
      static JLabel noPlayLabel = new JLabel();
      static JPanel[] playedCardPanels = new JPanel[Model.NUM_STACKS];
      static JLabel skipTurnLabel = new JLabel("Click here to skip your turn.");
      static JButton skipTurnButton = new JButton("I cannot play.");
      private static final Color COLOR_BLUE = new Color(0, 0, 255);
      //A label for the status text, for example, "You win!"
      static JLabel statusText = new JLabel("");
      static CardTable cardTable;
      private int getHandPosition(int player, JLabel chosenCard){
         for(int i = 0; i < this.playerHands[player].length; i++)
            if(this.playerHands[player][i] == chosenCard)
               return i;
         return -1;
      }
      private static void updateNoPlayLabel(int player1Score, int player2Score){
         noPlayLabel.setText("<html><u>No Play Counts</u><br>Computer: " + player1Score + "<br>You: " + player2Score + "</html>");
      }
      private static void updateTimer(String timerString){
         timerLabel.setText(timerString);
         cardTable.pnlTimer.revalidate();
         cardTable.pnlTimer.repaint();
      }
      private static void addButtonListener(){
         skipTurnButton.addActionListener(model.controller);
      }
      private static void removeButtonListener(){
         skipTurnButton.removeActionListener(model.controller);
      }
      private static void addStatusListener(){
         statusText.addMouseListener(model.controller);
      }
      private static void removeStatusListener(){
         statusText.setBorder(null);
         statusText.removeMouseListener(model.controller);
      }
      public View(String title, int numPlayers, int numCardsPerHand){
         this.cardTable = new CardTable(title, numCardsPerHand, numPlayers);
         this.playerHands = new JLabel[numPlayers][numCardsPerHand];
         this.playLabelText = new JLabel[numPlayers];
         for(int i = 0; i < numPlayers; i++) {
            playLabelText[i] = new JLabel();
            playLabelText[i].setHorizontalAlignment(JLabel.CENTER);
            playLabelText[i].setVerticalAlignment(JLabel.TOP);
            cardTable.pnlPlayerText.add(playLabelText[i]);
         }
         playedCardPanels[0] = new JPanel();
         playedCardPanels[1] = new JPanel();
         FlowLayout flow = new FlowLayout(FlowLayout.CENTER);
         playedCardPanels[0].setLayout(flow);
         playedCardPanels[1].setLayout(flow);
         cardTable.pnlPlayedCards.add(playedCardPanels[0]);
         cardTable.pnlPlayedCards.add(playedCardPanels[1]);
         playedCardLabels = new JLabel[NUM_PLAYERS];
         for(int i = 0; i < playedCardLabels.length; i++){
            playedCardLabels[i] = new JLabel();
            playedCardLabels[i].setHorizontalAlignment(JLabel.CENTER);
            playedCardLabels[i].addMouseListener(Phase3.controller);
            playedCardPanels[i].add(playedCardLabels[i]);
         }
         noPlayLabel.setHorizontalAlignment(JLabel.LEFT);
         noPlayLabel.setVerticalAlignment(JLabel.BOTTOM);
         cardTable.pnlNoPlays.add(noPlayLabel);
         statusText.setHorizontalAlignment(JLabel.CENTER);
         skipTurnButton.setHorizontalAlignment(JButton.CENTER);
         skipTurnButton.setFocusPainted(false);
         cardTable.pnlStatusText.add(statusText);
         cardTable.pnlStatusText.add(skipTurnButton);
         timerLabel.setHorizontalAlignment(JLabel.RIGHT);
         timerLabel.setVerticalAlignment(JLabel.BOTTOM);
         cardTable.pnlTimer.add(timerLabel);
      }
      private String getStatusText(){
         return statusText.getText();
      }
      private void setStatusText(String text){
         statusText.setText(text);
         cardTable.pnlStatusText.revalidate();
         cardTable.pnlStatusText.repaint();
      }
      private void setPlayedCard(int stack, Card card){
         if(card == null)
            playedCardLabels[stack].setIcon(null);
         else
            playedCardLabels[stack].setIcon(GUICard.getIcon(card));
      }
      private void setPlayLabelText(int player, String text){
         playLabelText[player].setText(text);
         cardTable.pnlPlayerText.revalidate();
         cardTable.pnlPlayerText.repaint();
      }
      public void drawHand(int player, Hand hand, boolean showCards){
         this.cardTable.handPanels[player].removeAll();
         this.playerHands[player] = new JLabel[NUM_CARDS_PER_HAND];
         for(int i = 0; i < hand.getNumCards(); i++){
            this.playerHands[player][i] = new JLabel();
            if(showCards) {
               this.playerHands[player][i].setIcon(GUICard.getIcon(hand.inspectCard(i)));
               this.playerHands[player][i].addMouseListener(Model.controller);
            }
            else
               this.playerHands[player][i].setIcon(GUICard.getBackCardIcon());
            this.cardTable.handPanels[player].add(this.playerHands[player][i]);
         }
         this.cardTable.handPanels[player].validate();
         this.cardTable.handPanels[player].repaint();
      }
      private void highlightLabel(JLabel label){
         label.setBorder(new LineBorder(COLOR_BLUE));
      }
      private void deHighlightLabel(JLabel label){
         label.setBorder(null);
      }
      private void showCardTable(){
         this.cardTable.setVisible(true);
      }
      /* GUICard implements various functions necessary to draw card images
        to the screen. */
      public static class GUICard {
         //Holds a card icon for each valid card.
         private static Icon[][] iconCards = new ImageIcon[14][4];
         private static Icon iconBack;
         static boolean iconsLoaded = false;
         static final char[] VALID_SUITS = {'C', 'D', 'H', 'S'};
         private static String iconFolderPath = "../images";

         /* In: A card object that the caller wants an image for
            Out: An Icon containing the image */
         public static Icon getIcon(Card card) {
            //Load all of the card icons if they haven't been already.
            if (!GUICard.iconsLoaded)
               GUICard.loadCardIcons();
            //return the appropriate card icon.
            return iconCards[valueAsInt(card)][suitAsInt(card)];
         }
         /* In: Nothing
            Out: Nothing
            Loads all of teh card icons from the images directory. If the images directory is not in the right place,
            then the user is prompted to select a directory where the images can be found. */
         private static void loadCardIcons() {
            //If the images folder doesn't exist,
            if (!(new File(GUICard.iconFolderPath).exists())) {
               //Prompt the user for a valid image folder.
               JOptionPane.showMessageDialog(null, "By deafult ../images/ is used to store card icon images, but ../images/ does not exist. Press OK to select the folder where card icon images are stored. Press cancel in the forthcoming dialog window to exit this program.");
               JFileChooser chooser = new JFileChooser(".");
               chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
               chooser.setMultiSelectionEnabled(false);
               chooser.showDialog(null, "Select");
               File selectedFile = chooser.getSelectedFile();
               //Exit the program if a valid image folder is not provided.
               if (selectedFile == null)
                  System.exit(0);
               GUICard.iconFolderPath = selectedFile.getPath();
               System.out.println(iconFolderPath);
            }

            //Load each of the cards into the appropriate position in the iconCards array.
            for (int i = 0; i < Card.validCardValues.length; i++)
               for (int j = 0; j < VALID_SUITS.length; j++) {
                  //If a card cannot be loaded, tell the user and exit the application.
                  if (!new File(iconFolderPath + "/" + Card.validCardValues[i] + VALID_SUITS[j] + ".gif").exists()) {
                     JOptionPane.showMessageDialog(null, Card.validCardValues[i] + VALID_SUITS[j] + ".gif could not be found in the icon folder. Program execution will now stop.");
                     System.exit(0);
                  }
                  iconCards[i][j] = new ImageIcon(iconFolderPath + "/" + Card.validCardValues[i] + VALID_SUITS[j] + ".gif");
               }
            //Load the back of the card icon.
            iconBack = new ImageIcon(iconFolderPath + "/BK.gif");
            GUICard.iconsLoaded = true; //Make sure this function is not called again.

         }
         /* In: A card object
            Out: An integer representing the row in iconCards that contains that value. */
         private static int valueAsInt(Card card) {
            String values = new String(Card.validCardValues);
            return values.indexOf(card.getValue());
         }
         /* In: A card object
            Out: An integer representing the column in the iconCards array that contains that suit. */
         private static int suitAsInt(Card card) {
            return card.getSuit().ordinal();
         }
         /* In: Nothing
            Out: An Icon object containing the back card icon. */
         public static Icon getBackCardIcon() {
            //Load all of the icons if they have not been already.
            if (!GUICard.iconsLoaded)
               GUICard.loadCardIcons();
            return GUICard.iconBack;
         }
      }
      /* CardTable implements JFRame in order to draw a GUI card table. It contains
        three main panels, one for the human's hand, one for the computer's, and one for
        the play area. */
      class CardTable extends JFrame {
         static final int MAX_CARDS_PER_HAND = 56;
         //This table only supports 2 player play.
         static final int MAX_PLAYERS = 2;
         private int numCardsPerHand;
         private int numPlayers;
         public JPanel[] handPanels;
         public JPanel pnlPlayArea, pnlPlayedCards, pnlPlayerText, pnlStatusText, pnlTimer, pnlNoPlays, pnlPlayedCardArea, pnlSkipTurn;
         /* In: [1] A String represetning the desired window title
                [2] An integer representing the number of cards per hand
                [3] An integer value representing the number of players playing on the table
            Out: Nothing. */
         public CardTable(String title, int numCardsPerHand, int numPlayers) {
            super(); //Call JFrame's constructor.
            this.handPanels = new JPanel[numPlayers];
            //Verify that the input is valid. Fix it if it is not.
            if (numCardsPerHand < 0 || numCardsPerHand > CardTable.MAX_CARDS_PER_HAND)
               this.numCardsPerHand = 20;
            this.numCardsPerHand = numCardsPerHand;
            if (numPlayers < 2 || numPlayers > CardTable.MAX_PLAYERS)
               this.numPlayers = numPlayers;
            if (title == null)
               title = "";
            //Set some of the window's attributes.
            this.setTitle(title);
            this.setSize(800, 600);
            this.setMinimumSize(new Dimension(800, 600));
            this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

            //The card table will use a BorderLayout style. This allows each panel
            //To have a different height. This allows for a larger play area and smaller
            //hand areas.
            BorderLayout layout = new BorderLayout();
            this.setLayout(layout);

            //Both the comptuer and human hand panels will use the flow layout.
            FlowLayout flowLayout = new FlowLayout(FlowLayout.LEFT);
            //Crate a titled border for the display of labels indicating
            //what each panel is for.
            TitledBorder border = new TitledBorder("Computer Hand");
            this.handPanels[0] = new JPanel();
            this.handPanels[0].setLayout(flowLayout);
            this.handPanels[0].setPreferredSize(new Dimension((int) this.getMinimumSize().getWidth() - 50, 105));
            //Use a JScrollPane in case the cards per hand is greater than can be displayed in the panel
            //without a scroll bar.
            JScrollPane scrollComputerHand = new JScrollPane(this.handPanels[0]);
            scrollComputerHand.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_NEVER);
            scrollComputerHand.setBorder(border);
            this.add(scrollComputerHand, BorderLayout.NORTH);

            //Create the playing area.
            border = new TitledBorder("Playing Area");
            //The play area will use a grid layout, so that the played cards, labels, and
            //status text can be displayed in neat columns.
            GridLayout gridLayoutCardsArea = new GridLayout(1, 2);
            GridLayout gridLayoutStatusArea = new GridLayout(2, 1);
            pnlPlayArea = new JPanel();
            pnlPlayArea.setBorder(border);
            layout = new BorderLayout();
            pnlPlayArea.setLayout(layout);
            pnlPlayedCardArea = new JPanel();
            pnlPlayedCardArea.setLayout(new GridLayout(2, 1));
            pnlTimer = new JPanel();
            pnlTimer.setLayout(gridLayoutStatusArea);
            pnlNoPlays = new JPanel();
            pnlNoPlays.setLayout(new GridLayout(3, 1));
            pnlPlayedCards = new JPanel();
            pnlPlayedCards.setLayout(gridLayoutCardsArea);
            pnlPlayerText = new JPanel();
            pnlPlayerText.setLayout(gridLayoutCardsArea);
            pnlStatusText = new JPanel();
            pnlStatusText.setLayout(gridLayoutStatusArea);
            //pnlPlayedCards.setPreferredSize(new Dimension((int) this.getMinimumSize().getWidth() - 50, 150));
            //pnlPlayerText.setPreferredSize(new Dimension(100, 30));
            //pnlStatusText.setPreferredSize(new Dimension(100, 30));
            pnlPlayedCardArea.add(pnlPlayedCards);
            pnlPlayedCardArea.add(pnlPlayerText);
            pnlPlayArea.add(pnlTimer, BorderLayout.EAST);
            pnlPlayArea.add(pnlNoPlays, BorderLayout.WEST);
            pnlPlayArea.add(pnlPlayedCardArea, BorderLayout.CENTER);
            pnlPlayArea.add(pnlStatusText, BorderLayout.SOUTH);
            this.add(pnlPlayArea, BorderLayout.CENTER);
            ///Create the human's hand area.
            border = new TitledBorder("Human Hand");
            this.handPanels[1] = new JPanel();
            this.handPanels[1].setLayout(flowLayout);
            this.handPanels[1].setPreferredSize(new Dimension((int) this.getMinimumSize().getWidth() - 50, 105));
            JScrollPane scrollHumanHand = new JScrollPane(this.handPanels[1]);
            scrollHumanHand.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_NEVER);
            scrollHumanHand.setBorder(border);
            this.add(scrollHumanHand, BorderLayout.SOUTH);
         }
      }
   }
   private static class Controller implements MouseListener, ActionListener{
      private static JLabel selectedCard = null;
      private static boolean stackClickable = false;
      /* In: A MouseEvent object
     Out: Nothing
     Fires when the mouse enters a card or status JLabel */
      public void mouseEntered(MouseEvent e) {
         JLabel source = (JLabel)e.getSource();
         if(source == view.playedCardLabels[0] || source == view.playedCardLabels[1]) {
            if (stackClickable)
               view.highlightLabel(source);
         }
         else
            view.highlightLabel(source);
      }
      /* In: A MouseEvent object
         Out: Nothing
         Fires when the mouse exits a card or status JLabel */
      public void mouseExited(MouseEvent e) {
         JLabel source = (JLabel)e.getSource();
         if(source != selectedCard)
            view.deHighlightLabel(source);
      }
      /* In: A MouseEvent object
         Out: Nothing
         Fires when the mouse clicks a card or status JLabel */
      public void mouseClicked(MouseEvent e) {
         //The source will always be a JLabel
         JLabel source = (JLabel)e.getSource();
         if(source == view.statusText){
            model.initGame();
            return;
         }
         //Determine if the label being clicked is a card in the players hand.
         for(int playerHand = 0; playerHand < view.playerHands.length; playerHand++){
            for(int card = 0; card < model.highCardGame.getHand(playerHand).getNumCards(); card++){
               if(view.playerHands[playerHand][card].getIcon() == View.GUICard.getBackCardIcon())
                  continue;
               if(view.playerHands[playerHand][card] == source){
                  //A card was clicked.
                  if(source != selectedCard && selectedCard != null)
                     view.deHighlightLabel(selectedCard);
                  selectedCard = source;
                  model.selectedCard = card;
                  stackClickable = true;
                  view.setStatusText("Click on the stack you would like to place the selected card on.");
                  view.highlightLabel(source);
                  return;
               }
            }
         }
         //If this function has gone this far, determine if the label being clicked is
         //one of the stacks, as long as stackClickable is true.
         if(stackClickable){
            boolean cardPlaced = false;
            if(source == view.playedCardLabels[0])
               cardPlaced = model.placeCard(0);
            else if(source == view.playedCardLabels[1])
               cardPlaced = model.placeCard(1);
            if(cardPlaced){
               stackClickable = false;
               view.deHighlightLabel(source);
               selectedCard = null;
            }
         }
      }
      public void actionPerformed(ActionEvent e){
         //The only button is the skip turn button, so just skip the turn here.
         model.skipPlayerTurn();
      }
      //Not used.
      public void mouseReleased(MouseEvent e) {

      }
      //Not used.
      public void mousePressed(MouseEvent e) {

      }
   }
}
/* Class Deck represents a deck of cards consisting of a variable number of 52 card packs. It
         contains a master pack, which the deck is built off of. It also contains member functions
         that would be expected of a deck of cards, providing functionality like shuffling
         and dealing.
      */
class Deck {
   public static final short MAX_CARDS_IN_PACK = 56;
   public static final short MAX_PACKS = 6;
   public static final short MAX_CARDS = MAX_PACKS * MAX_CARDS_IN_PACK;
   //The masterPack is a pack of cards that the cards in the deck are built off of.
   //It contains one card for each value/suit combination. This is static,
   //as it does not change per object instantiated.
   private static Card[] masterPack = new Card[MAX_CARDS_IN_PACK];
   private Card[] cards; //The cards in the object's deck. Not static, as each deck object can have different cards.
   private int topCard; //The position of the card on the top of the deck.
   private int numPacks; //The deck can consist of multiple packs of cards.

   /* Deck(int)
      In: An integer specifying the number of packs to build the deck from.
      Out: Nothing
      Description: This is a constructor that will build a deck composed of the
                   specified number of packs.
   */
   public Deck(int numPacks) {
      //Build the master pack.
      this.allocateMasterPack();
      //If the user wants more packs than are available, give them the max.
      if (numPacks > Deck.MAX_PACKS)
         this.init(Deck.MAX_PACKS);
         //If the user wants 0 or less packs, give them one.
      else if (numPacks < 1)
         this.init(1);
      else
         //Otherwise, build the deck with the specified number of packs.
         this.init(numPacks);
   }

   /* Deck()
      In: None
      Out: Nothing
      Description: This default constructor builds a deck with one pack.
   */
   public Deck() {
      this.allocateMasterPack();
      this.init(1);
   }

   /* void init(int)
      In: An integer whose value is the number of packs to build the deck from.
      Out: Nothing
      Description: This will initialize the cards array data member to a complete deck built
                   from the specified number of packs.
   */
   public void init(int numPacks) {
      //Initialize the cards array.
      this.cards = new Card[numPacks * Deck.MAX_CARDS_IN_PACK];
      //Until the total number of cards are reached, keep adding cards from the
      //master pack.
      for (int i = 0; i < numPacks * Deck.MAX_CARDS_IN_PACK; i++) {
         this.cards[i] = this.masterPack[i % Deck.MAX_CARDS_IN_PACK];
      }
      //Set the top card to the last card allocated.
      this.topCard = numPacks * Deck.MAX_CARDS_IN_PACK;
   }

   /* void shuffle()
      In: Nothing
      Out: Nothing
      Description: This uses a Fisher-Yates shuffle to shuffle all of the cards in the
                   deck.
   */
   public void shuffle() {
      //Beginning with the top card, decrement i until i is 0.
      for (int i = this.topCard - 1; i >= 0; i--) {
         Card tmpCard = this.cards[i]; //Store the card at i, since it will be overwritten.
         //Choose a random card position from within the deck.
         int randomPosition = (int) (Math.random() * (this.topCard - 1));
         //Take the card from the random position and store it in the ith position.
         this.cards[i] = this.cards[randomPosition];
         //Take the card from the ith position, and put it into the randomly chosen position.
         this.cards[randomPosition] = tmpCard;
         //The cards have now been swapped.
      }
   }

   /* Card dealCard()
      In: Nothing
      Out: A copy of the Card object on the top of the deck.
      Description: This function makes a copy of the card on the top of the deck,
                   removes that card from the deck, and returns the copy to the caller.
   */
   public Card dealCard() {
      //Return an invalid card if there are no cards in the deck.
      if (this.topCard <= 0)
         return new Card('0', Card.Suit.spades);
      else {
         //Create a copy of the card on the top of the deck.
         Card card = new Card(this.cards[this.topCard - 1]);
         //Set the actual card on the top of the deck to null, to destroy it.
         this.cards[this.topCard - 1] = null;
         //The topCard is now one less than it was.
         this.topCard--;
         //return the copy.
         return card;
      }
   }

   /* int getTopCard()
      In: Nothing
      Out: An integer whose value is the position of the top card in the deck.
      Description: This is a basic accessor function.
   */
   public int getTopCard() {
      return this.topCard;
   }

   /* Card inspectCard(int)
      In: An integer representing the position of the card to be inspected.
      Out: A copy of the card at the specified position, or an invalid card if there is no
           card in that position.
      Description: This function returns a Card object whose values are equal to the card in
                   the specified position.
   */
   public Card inspectCard(int k) {
      //If k is invalid, return an invalid card.
      if (k >= this.topCard || k < 0)
         return new Card('0', Card.Suit.spades);
      else
         //Otherwise, return a copy of the card in position k.
         return new Card(this.cards[k]);
   }

   /* void allocateMasterPack()
      In: Nothing
      Out: Nothing
      Description: This function fills the masterPack if it is not already filled. It fills the pack
                   with valid card values.
   */
   private static void allocateMasterPack() {
      //If Deck.masterPack is null, then it needs to be filled, otherwise, nothing needs to be done.
      if (Deck.masterPack != null) {
         //For each suit, fill the masterPack with each valid card value from that suit.
         for (int i = 0; i < Card.Suit.values().length; i++) {
            for (int j = 0; j < Card.validCardValues.length; j++) {
               Deck.masterPack[i * Card.validCardValues.length + j] = new Card(Card.validCardValues[j], Card.Suit.values()[i]);
            }
         }
      }
   }
   /* In: A card object to add to the deck
      Out: A boolean value indicating whether the card was able to be added to the deck */
   public boolean addCard(Card card) {
      int cardCount = 0;
      //Check to see if the deck already has the maximum number of cards
      //of this type.
      for (Card cardInDeck : this.cards)
         if (cardInDeck.equals(card))
            cardCount++;
      //Return false is the card will not fit, or if it is invalid.
      if (cardCount >= this.numPacks || this.topCard >= this.MAX_CARDS || card.errorFlag)
         return false;
      this.topCard++;
      //Add the card object to the deck.
      this.cards[topCard - 1] = new Card(card);
      return true;
   }
   /* In: Nothing
      Out: An itneger indicating the number of cards in the deck. */
   public int getNumCards() {
      return this.topCard;
   }
   /* In: A card object to remove from the deck
      Out: A boolean value indicating if the card was able to be removed from the deck. */
   public boolean removeCard(Card card) {
      //Iterate through the deck to find the card.
      for (int i = 0; i < this.cards.length; i++)
         if (this.cards[i].equals(card)) {
            //If the card is found, then remove it from the deck.
            //replace it with the topCard
            this.cards[i] = new Card(this.cards[topCard - 1]);
            this.topCard--;
            return true;
         }
      return false;
   }
   /* In: Nothing
      Out: Nothing
      Sorts the cards in the deck */
   public void sort() {
      Card.arraySort(this.cards, this.topCard);
   }
}
/* Class Card represents a typical card that would be found in a deck of playing cards.
         It has private members to hold the value and suit of the card. It also has methods
         to validate and set these data members.
      */
class Card implements Comparable {
   //The four standard suits are supported.
   public enum Suit {
      clubs, diamonds, hearts, spades
   };
   private char value;
   private Suit suit;
   //errorFlag is set to true if the user tries to create or set a card's value
   //to one that is not in the validCardValues array. This will cause the card's
   //toString() method to indicate that the card is invalid.
   boolean errorFlag;
   //validCardValues holds values that a card is allowed to be.
   public static char[] validCardValues = {'A', '2', '3', '4', '5', '6', '7', '8', '9', 'T', 'J', 'Q', 'K', 'X'};
   public static char[] validCardSuits = {'C', 'D', 'H', 'S'};
   public static char[] valueRanks = validCardValues;

   /* In: An object
      Out: An int indicating if the object is less than, greater than, or equal to
           the object performing the comparison. */
   public int compareTo(Object t) {
      if (t.getClass() != this.getClass())
         return 1;
      Card c = (Card) t;
      String strRanks = new String(valueRanks);
      if (strRanks.indexOf(c.getValue()) < 0)
         return 1;
      if (strRanks.indexOf(c.getValue()) < strRanks.indexOf(this.getValue()))
         return 1;
      if (strRanks.indexOf(c.getValue()) == strRanks.indexOf(this.getValue()))
         return 0;
      if (strRanks.indexOf(c.getValue()) > strRanks.indexOf(this.getValue()))
         return -1;
      return 1;
   }
   /* In: [1] An array of card objects to be sorted
          [2] The number of objects in parameter 1
      This uses a bubble sort to sort the cards in the cards array. */
   static void arraySort(Card[] cards, int arraySize) {
      //Swapped will change to true if any swapping occurs in the
      //loop below.
      boolean swapped = false;
      do {
         swapped = false;
         //Go through each element in the array
         for (int i = 1; i < arraySize; i++) {
            //If an element is larger thant he one after it,
            if (cards[i - 1].compareTo(cards[i]) > 0) {
               //Swap those elements.
               Card tmpCard = new Card(cards[i - 1]);
               cards[i - 1] = new Card(cards[i]);
               cards[i] = new Card(tmpCard);
               swapped = true;
            }
         }
      } while (swapped); //Continue until this loop runs with no swapping.
   }
   /* Card(char, Suit)
      In: A char representing the card's value, and a Suit representing the card's suit.
      Out: Nothing
      Description: This is a constructor that takes a value and a suit for a card. This will
                   create a card of the specified value and suit.
   */
   public Card(char value, Suit suit) {
      this.set(value, suit);
   }

   /* Card()
      In: Nothing
      Out: Nothing
      Description: This is a default constructor that takes no values. It will create an Ace of Spades.
   */
   public Card() {
      this.set('A', Suit.spades);
   }

   /* Card(Card)
      In: A Card object
      Out: Nothing
      Description: This is a copy constructor that returns a NEW card with the same values as the card
                   passed into it.
   */
   public Card(Card card) {
      this.set(card.value, card.suit);
   }

   /* boolean set(char, Suit)
      In: A char representing the card's value and a Suit representing the card's suit.
      Out: True if the value and suit are valid, false if otherwise.
      Description: This set's the card's suit and value, if they are valid. Otherwise,
                   it sets the card's errorFlag to true.
   */
   public boolean set(char value, Suit suit) {
      if (Card.isValid(value, suit)) {
         this.errorFlag = false;
         this.value = value;
         this.suit = suit;
         return true;
      } else {
         this.errorFlag = true;
         return false;
      }
   }

   /* boolean isValid(char, Suit)
      In: A char representing the card's value and a Suit representing its suit.
      Out: True if the value is valid, false if otherwise.
      Description: This function determines whether the value passed to it is a valid
                   value for a card. It checks the value against the valid values stored
                   in Card.validCardValues.
   */
   private static boolean isValid(char value, Suit suit) {
      for (char validValue : Card.validCardValues)
         if (String.valueOf(validValue).toLowerCase().equals(String.valueOf(value).toLowerCase()))
            return true;
      return false;
   }

   /* char getValue()
      In: Nothing
      Out: A char holding the card's value.
      Description: This is an accessor for the card's value.
   */
   public char getValue() {
      return value;
   }

   /* Suit getSuit()
      In: Nothing
      Out: The card's suit type.
      Description: This is an accessor for the card's suit.
   */
   public Suit getSuit() {
      return this.suit;
   }

   /* String toString()
      In: Nothing
      Out: A String object containing the value and suit of the card,
           or [INVALID CARD] if the errorFlag is set to true.
      Description: This returns the card's value to the caller in String form.
   */
   public String toString() {
      if (this.errorFlag == true)
         return "[INVALID CARD]";
      else
         return this.value + " of " + suit.toString();
   }
   /* In: A card object
      Out: A boolean value. True if the cards are equal, false if otherwise. */
   public boolean equals(Card c) {
      if (this.getValue() == c.getValue() && this.getSuit() == c.getSuit())
         return true;
      return false;
   }
}
/* Class Hand represents a hand of cards. This is much like a collection of cards, but provides methods
   or interaction with the cards that are specific to a "hand," rather than a collection.
*/
class Hand {
   public static final int MAX_CARDS = 50;
   private Card[] myCards = new Card[MAX_CARDS];
   int numCards = 0;

   /* Hand()
      In: Nothing
      Out: Nothing
      Description: The default constructor for Hand does not actually do anything.
   */
   public Hand() {
   }
   /* In: Nothing
      Out: Nothing
      Sorts all of the cards in the hand object. */
   void sort() {
      Card.arraySort(this.myCards, numCards);
   }

   /* void resetHand()
      In: Nothing
      Out: Nothing
      Description: This sets the hand to its default state, containing no cards.
   */
   public void resetHand() {
      this.myCards = new Card[MAX_CARDS];
      this.numCards = 0;
   }

   /* boolean takeCard(Card)
      In: A Card object
      Out: True if there is room in the hand for the card, false if otherwise
      Description: This takes a Card object and places a copy of that object into the hand.
   */
   public boolean takeCard(Card card) {
      if (this.numCards >= MAX_CARDS)
         return false;
      else {
         this.myCards[numCards] = new Card(card);
         this.numCards++;
         return true;
      }
   }

   /* Card playCard()
     In: Nothing
     Out: A Card object with the same values as the card on the top of the hand.
     Description: This creates a copy of the first card in the hand and returns it to the caller.
  */
   public Card playCard() {
      Card card = this.myCards[this.numCards - 1];
      this.myCards[this.numCards - 1] = null;
      this.numCards--;
      return card;
   }

   /* String toString()
     In: Nothing
     Out: A String object containing the cards in the hand.
     Description: This will provide a textual representation of the data contained in hand to the caller.
  */
   public String toString() {
      String handString = "( ";
      for (int i = 0; i < this.numCards; i++) {
         handString += this.myCards[i].toString();
         if (i != this.numCards - 1)
            handString += ", ";
      }
      handString += " )";
      return handString;
   }

   /* int getNumCards()
     In: Nothing
     Out: An integer whose value is the number of cards in the hand.
     Description: This is a basic accessor function.
  */
   public int getNumCards() {
      return this.numCards;
   }

   /* Card inspectCard(int)
      In: An integer representing the position of the card to be inspected.
      Out: A copy of the card at the specified position, or an invalid card if there is no
           card in that position.
      Description: This function returns a Card object whose values are equal to the card in
                   the specified position.
   */
   public Card inspectCard(int k) {
      if (k >= this.numCards || k < 0)
         return new Card('0', Card.Suit.spades);
      else
         return new Card(this.myCards[k]);
   }

   /* In: An integer specifying the position of the card to play in the hand
      Out: The Card object representing the card in that position
      This plays a card and removes it from the hand. */
   public Card playCard(int k) {
      //If k is invalid, return an invalid card.
      if (k >= this.numCards || k < 0)
         return new Card('0', Card.Suit.spades);
      else {
         //Return the card in that position, and
         //move all of the cards after that card
         //back by one position.
         Card card = new Card(this.myCards[k]);
         for (int i = k + 1; i < this.numCards; i++) {
            this.myCards[i - 1] = this.myCards[i];
            this.myCards[i] = null;
         }
         this.numCards--;
         return card;
      }
   }
}
//class CardGameFramework  ----------------------------------------------------
class CardGameFramework {
   private static final int MAX_PLAYERS = 50;

   private int numPlayers;
   private int numPacks;            // # standard 52-card packs per deck
   // ignoring jokers or unused cards
   private int numJokersPerPack;    // if 2 per pack & 3 packs per deck, get 6
   private int numUnusedCardsPerPack;  // # cards removed from each pack
   private int numCardsPerHand;        // # cards to deal each player
   private Deck deck;               // holds the initial full deck and gets
   // smaller (usually) during play
   private Hand[] hand;             // one Hand for each player
   private Card[] unusedCardsPerPack;   // an array holding the cards not used
   // in the game.  e.g. pinochle does not
   // use cards 2-8 of any suit

   Card playCard(int playerIndex, int cardIndex)
   {
      // returns bad card if either argument is bad
      if (playerIndex < 0 ||  playerIndex > numPlayers - 1 ||
            cardIndex < 0 || cardIndex > numCardsPerHand - 1)
      {
         //Creates a card that does not work
         return new Card('M', Card.Suit.spades);
      }

      // return the card played
      return hand[playerIndex].playCard(cardIndex);

   }
   boolean takeCard(int playerIndex, int cardIndex)
   {
      // returns false if either argument is bad
      if (playerIndex < 0 ||  playerIndex > numPlayers - 1 ||
            cardIndex < 0 || cardIndex > numCardsPerHand - 1)
      {
         return false;
      }

      // Are there enough Cards?
      if (deck.getNumCards() <= 0)
         return false;

      return hand[playerIndex].takeCard(deck.dealCard());
   }
   public CardGameFramework(int numPacks, int numJokersPerPack,
                            int numUnusedCardsPerPack, Card[] unusedCardsPerPack,
                            int numPlayers, int numCardsPerHand) {
      int k;

      // filter bad values
      if (numPacks < 1 || numPacks > 6)
         numPacks = 1;
      if (numJokersPerPack < 0 || numJokersPerPack > 4)
         numJokersPerPack = 0;
      if (numUnusedCardsPerPack < 0 || numUnusedCardsPerPack > 50) //  > 1 card
         numUnusedCardsPerPack = 0;
      if (numPlayers < 1 || numPlayers > MAX_PLAYERS)
         numPlayers = 4;
      // one of many ways to assure at least one full deal to all players
      if (numCardsPerHand < 1 ||
            numCardsPerHand > numPacks * (52 - numUnusedCardsPerPack)
                  / numPlayers)
         numCardsPerHand = numPacks * (52 - numUnusedCardsPerPack) / numPlayers;

      // allocate
      this.unusedCardsPerPack = new Card[numUnusedCardsPerPack];
      this.hand = new Hand[numPlayers];
      for (k = 0; k < numPlayers; k++)
         this.hand[k] = new Hand();
      deck = new Deck(numPacks);

      // assign to members
      this.numPacks = numPacks;
      this.numJokersPerPack = numJokersPerPack;
      this.numUnusedCardsPerPack = numUnusedCardsPerPack;
      this.numPlayers = numPlayers;
      this.numCardsPerHand = numCardsPerHand;
      for (k = 0; k < numUnusedCardsPerPack; k++)
         this.unusedCardsPerPack[k] = unusedCardsPerPack[k];

      // prepare deck and shuffle
      newGame();
   }

   // constructor overload/default for game like bridge
   public CardGameFramework() {
      this(1, 0, 0, null, 4, 13);
   }

   public Hand getHand(int k) {
      // hands start from 0 like arrays

      // on error return automatic empty hand
      if (k < 0 || k >= numPlayers)
         return new Hand();

      return hand[k];
   }

   public Card getCardFromDeck() {
      return deck.dealCard();
   }

   public int getNumCardsRemainingInDeck() {
      return deck.getNumCards();
   }

   public void newGame() {
      int k, j;

      // clear the hands
      for (k = 0; k < numPlayers; k++)
         hand[k].resetHand();

      // restock the deck
      deck.init(numPacks);

      // remove unused cards
      for (k = 0; k < numUnusedCardsPerPack; k++)
         deck.removeCard(unusedCardsPerPack[k]);

      // add jokers
      for (k = 0; k < numPacks; k++)
         for (j = 0; j < numJokersPerPack; j++)
            deck.addCard(new Card('X', Card.Suit.values()[j]));

      // shuffle the cards
      deck.shuffle();
   }

   public boolean deal() {
      // returns false if not enough cards, but deals what it can
      int k, j;
      boolean enoughCards;

      // clear all hands
      for (j = 0; j < numPlayers; j++)
         hand[j].resetHand();

      enoughCards = true;
      for (k = 0; k < numCardsPerHand && enoughCards; k++) {
         for (j = 0; j < numPlayers; j++)
            if (deck.getNumCards() > 0)
               hand[j].takeCard(deck.dealCard());
            else {
               enoughCards = false;
               break;
            }
      }

      return enoughCards;
   }

   void sortHands() {
      int k;

      for (k = 0; k < numPlayers; k++)
         hand[k].sort();
   }
}
