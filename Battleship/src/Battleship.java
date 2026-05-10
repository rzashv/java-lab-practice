import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;

/**
 * PA #2 -- Battleship
 *
 * Implement a single-player Battleship game on a 10x10 board.
 *
 * Startup input (one line from stdin):
 *   N MODE FILE_NAME
 *   - N        : number of bombs (positive integer)
 *   - MODE     : d/D (Debug) or r/R (Release)
 *   - FILE_NAME: board file path (may contain spaces)
 *
 * Submit this file as: Battleship.java
 * - Public class name must be exactly "Battleship"
 * - No Korean comments allowed
 * - Must compile cleanly: javac Battleship.java
 */

public class Battleship {

    private static final int  BOARD_SIZE  = 10;
    private static final long RANDOM_SEED =
        Long.parseLong(System.getProperty("seed", "2026"));

    // === Board State ===
    private char[][] baseBoard;  //  char[][]  baseBoard  -- ship characters or ' '
    private boolean[][] shot;  // boolean[][] shot -- true if this cell has been targeted
    private Ship[][] shipRef;  // Ship[][]  shipRef -- reference to the Ship object at each cell
    private int score;

    // === Entry Point ===
    

    public static void main(String[] args) {
        try {
	    	BufferedReader reader = new BufferedReader(new InputStreamReader(System.in)); // 1. Create a BufferedReader from System.in
	    	StartupConfig config;
	    	
	    	try {  // 2. Call parseStartupLine() -- catch BombInputException / ModeInputException,
	    		config = parseStartupLine(reader);
	    	}
	    	catch (BombInputException e) {  // print the exception class simple name, and return
	    		System.out.println("BombInputException");
	    		return;
	    	}
	    	catch (ModeInputException e) {
	    		System.out.println("ModeInputException");
	    		return;
	    	}
	    	
	    	Battleship game = new Battleship();  // 3. Create a Battleship instance and call initializeBoard()
	    	game.initializeBoard(config.fileName);
	    	
	    	game.play(config.bombs, config.mode, reader); // 4. Call play()
        }
        catch (IOException e) { // 5. Catch IOException: print "IOException" to stdout and return
        	System.out.println("IOException");
        	return;
        }
    	
    	
    }

    // === Startup Parsing ===

    /**
     * Reads one non-empty line from reader and parses it as:
     *   N MODE FILE_NAME
     *
     * FILE_NAME is everything after MODE (may contain spaces).
     *
     * @throws BombInputException  if N is missing, not an integer, or <= 0
     * @throws ModeInputException  if MODE is not one of d, D, r, R,
     *                             or if MODE/FILE_NAME tokens are missing
     */
    private static StartupConfig parseStartupLine(BufferedReader reader)
            throws IOException, BombInputException, ModeInputException {
    	String line;
    	do {  // executes at least once == reads one non-empty line from reader
    		line = reader.readLine();
    	} while (line != null && line.trim().isEmpty());
    	if (line == null) {
    		throw new BombInputException();
    	}
    	
    	String[] parts = line.trim().split("\\s+", 3);  //exactly 3 splits in case the name contains any whitespaces
    	
    	int bombs;
    	if (parts.length < 1) throw new BombInputException();  // N is missing
    	try {
    		bombs = Integer.parseInt(parts[0]);
    		if (bombs <= 0) throw new BombInputException();
    	}
    	catch (Exception e) {
    		throw new BombInputException();
    	}
    	
    	if (parts.length < 2 || parts[1].length() != 1) throw new ModeInputException();  // mode is missing
    	char m = parts[1].charAt(0);
    	Mode mode;
    	
    	if (m == 'd' || m == 'D') {
    		mode = Mode.DEBUG;
    	}
    	else if (m == 'r' || m == 'R') {
    		mode = Mode.RELEASE;
    	}
    	else {
    		throw new ModeInputException();
    	}
    	
    	if (parts.length < 3) throw new ModeInputException();  // file name is missing
    	String fileName = parts[2];
    	
        return new StartupConfig(bombs, mode, fileName);
    }

    // === Board Initialisation ===

    /**
     * Initializes all board state to empty / false / null.
     * Resets score to 0.
     */
    private void clearBoard() {
    	baseBoard = new char[BOARD_SIZE][BOARD_SIZE];
    	shot = new boolean[BOARD_SIZE][BOARD_SIZE];
    	shipRef = new Ship[BOARD_SIZE][BOARD_SIZE];
    	score = 0;
    	
        for (int i = 0; i < BOARD_SIZE; i++) {
        	for (int j = 0; j < BOARD_SIZE; j++) {
        		baseBoard[i][j] = ' ';
        		shot[i][j] = false;
        		shipRef[i][j] = null;
        	}
        }
    }

    /**
     * If the file at fileName exists, calls loadBoardFromFile().
     * Otherwise calls generateRandomBoard(new Random(RANDOM_SEED)).
     */
    private void initializeBoard(String fileName) throws IOException {
    	clearBoard();
    	
        if (java.nio.file.Files.exists(Path.of(fileName))) {
        	loadBoardFromFile(Path.of(fileName));
        }
        else {
        	generateRandomBoard(new Random(RANDOM_SEED));
        }
    }

    /**
     * Reads a 10-line board file. Each line is exactly 10 characters
     * (space-pad lines shorter than 10). Valid ship characters: A B S D P.
     * Populates baseBoard and shipRef.
     *
     * Ship segments are recognized:
     *   - Horizontal: consecutive same-type characters in the same row (length >= 2)
     *   - Vertical  : consecutive same-type characters in the same column
     *   - Isolated single cell: treated as its own ship object
     */
    private void loadBoardFromFile(java.nio.file.Path path) throws IOException {
        BufferedReader br = Files.newBufferedReader(path);
        String line;
        for (int i = 0; i < BOARD_SIZE; i++) {
        	line = br.readLine();
        	if (line == null) {
        		line = "";
        	}
        	
        	while (line.length() < BOARD_SIZE) {
        		line = line + " ";
        	}
        	
        	for (int j = 0; j < BOARD_SIZE; j++) {
        		baseBoard[i][j] = line.charAt(j);
        	}
        }
        
        boolean[][] visited = new boolean[BOARD_SIZE][BOARD_SIZE];
        for (int i = 0; i < BOARD_SIZE; i++) {
        	for (int j = 0; j < BOARD_SIZE; j++) {
        		if (baseBoard[i][j] == ' ') continue;
        		if (visited[i][j]) continue;
        		
        		char type = baseBoard[i][j];
        		boolean horizontal = false;
        		boolean vertical = false;
        		
        		if (j + 1 < BOARD_SIZE && baseBoard[i][j+1] == type) {
        			horizontal = true;
        		}
        		else if (i + 1 < BOARD_SIZE && baseBoard[i+1][j] == type) {
        			vertical = true;
        		}
        		
        		int len = 1;
        		if (horizontal) {
        			int col = j +1;
        			while (col < BOARD_SIZE && baseBoard[i][col] == type) {
        				len++;
        				col++;
        			}
        		}
        		else if (vertical) {
        			int row = i + 1;
        			while (row < BOARD_SIZE && baseBoard[row][j] == type) {
        				len++;
        				row++;
        			}
        		}
        		Ship ship = null;
        		if(type == 'A') {
        			ship = new AircraftCarrier();
        		}
        		else if (type == 'B') {
        			ship = new BattleshipShip();
        		}
        		else if (type == 'S') {
        			ship = new Submarine();
        		}
        		else if (type == 'D') {
        			ship = new Destroyer();
        		}
        		else if (type == 'P') {
        			ship = new PatrolBoat();
        		}
        		
        		if (horizontal) {
        			for (int col = j; col < j+len; col++) {
        				shipRef[i][col] = ship;
        				visited[i][col] = true;
        			}
        		}
        		else if (vertical) {
        			for (int row = i; row < i+len; row++) {
        				shipRef[row][j] = ship;
        				visited[row][j] = true;
        			}
        		}
        		else {
        			shipRef[i][j] = ship;
    				visited[i][j] = true;
        		}
        		
        	}
        }
        
        br.close();
    }

    /**
     * Places all ships randomly using the provided Random instance.
     *
     * Ship placement order (MUST follow exactly for deterministic output):
     *   AircraftCarrier x1, BattleshipShip x2, Submarine x2,
     *   Destroyer x1, PatrolBoat x4
     *
     * Per attempt:
     *   boolean horizontal = rng.nextBoolean();
     *   int row = rng.nextInt(10);
     *   int col = rng.nextInt(10);
     *
     * Retry (call rng again in the same order) if the placement is invalid.
     */
    private void generateRandomBoard(Random rng) {
        Ship aircraft = new AircraftCarrier();
        while (true) {
        	boolean horizontal = rng.nextBoolean();
        	int row = rng.nextInt(10);
        	int col = rng.nextInt(10);
        	
        	if (canPlace(row, col, aircraft.size, horizontal)) {
        		placeShip(aircraft, row, col, horizontal);
        		break;
        	}
        }
        
        for (int i = 0; i < 2; i++) {
        	Ship ship = new BattleshipShip();
        	while (true) {
            	boolean horizontal = rng.nextBoolean();
            	int row = rng.nextInt(10);
            	int col = rng.nextInt(10);
            	
            	if (canPlace(row, col, ship.size, horizontal)) {
            		placeShip(ship, row, col, horizontal);
            		break;
            	}
            }
        }
        
        for (int i = 0; i < 2; i++) {
        	Ship sub = new Submarine();
        	while (true) {
            	boolean horizontal = rng.nextBoolean();
            	int row = rng.nextInt(10);
            	int col = rng.nextInt(10);
            	
            	if (canPlace(row, col, sub.size, horizontal)) {
            		placeShip(sub, row, col, horizontal);
            		break;
            	}
            }
        }
        
        Ship destroyer = new Destroyer();
    	while (true) {
        	boolean horizontal = rng.nextBoolean();
        	int row = rng.nextInt(10);
        	int col = rng.nextInt(10);
        	
        	if (canPlace(row, col, destroyer.size, horizontal)) {
        		placeShip(destroyer, row, col, horizontal);
        		break;
        	}
        }
    	
    	for (int i = 0; i < 4; i++) {
        	Ship patrol = new PatrolBoat();
        	while (true) {
            	boolean horizontal = rng.nextBoolean();
            	int row = rng.nextInt(10);
            	int col = rng.nextInt(10);
            	
            	if (canPlace(row, col, patrol.size, horizontal)) {
            		placeShip(patrol, row, col, horizontal);
            		break;
            	}
            }
        }
    }

    /**
     * Returns true if a ship of the given size can be placed at (row, col)
     * in the given direction without overlapping or touching any existing ship.
     */
    private boolean canPlace(int row, int col, int size, boolean horizontal) {
        int endRow = row;
        int endCol = col;
        if (horizontal) {
        	endCol = col + size - 1;
        }
        else {
        	endRow = row + size - 1;
        }
        if (endRow >= BOARD_SIZE || endCol >= BOARD_SIZE) {
        	return false;
        }
        
        for (int i = row - 1; i <= endRow + 1; i++) {
        	for (int j = col - 1; j <= endCol + 1; j++) {
        		if (i >= 0 && i < BOARD_SIZE && j >= 0 && j < BOARD_SIZE) {
        			if (shipRef[i][j] != null ) {
        				return false;
        			}
        		}
        	}
        } 
        return true;
    }

    /**
     * Places the ship on the board starting at (row, col).
     * Updates baseBoard and shipRef for every cell the ship occupies.
     */
    private void placeShip(Ship ship, int row, int col, boolean horizontal) {
    	for (int i = 0; i < ship.size; i++) {
    		int r = row;
    		int c = col;
    		if (horizontal) {
    			c = col + i; // loop through the columns
    		}
    		else {
    			r = r + i; //loop through the rows
    		}
    		shipRef[r][c] = ship;
    		baseBoard[r][c] = ship.type;
    	}
    }

    // === Game Loop ===

    /**
     * Main game loop.
     *
     * Repeats until all bombs are used:
     *   - Debug mode  : print board, then read a coordinate
     *   - Release mode: read a coordinate (no board print)
     *
     * After processing each input:
     *   - Valid new coordinate : call shoot(), increment bomb counter
     *   - Invalid / repeated   : print "Try again", do NOT increment counter
     *
     * When all bombs are used: print final board, then "Score N".
     */
    private void play(int bombs, Mode mode, BufferedReader reader) throws IOException {
    	int usedBombs = 0;
        while (usedBombs < bombs) {
        	if (mode == Mode.DEBUG) {
        		printBoard();
        	}
        	
        	String coordinate = reader.readLine();
        	
    		try {
    			int[] coorConverted = parseCoordinate(coordinate);
    			shoot(coorConverted[0], coorConverted[1]);
    			usedBombs++;
    		} catch (HitException e) {
    			System.out.println("Try again");
    		}
        }
        printBoard();
        System.out.println("Score " + score);
    }

    /**
     * Parses a coordinate string (e.g., "A1", "j10").
     *
     * Rules:
     *   - First character must be a letter A-J (case-insensitive)
     *   - Remaining characters must form an integer 1-10
     *   - The cell must not have been shot before
     *
     * Throws HitException for any invalid or repeated input.
     * Returns int[]{row, col} (0-indexed) on success.
     */
    private int[] parseCoordinate(String token) throws HitException {
        if (token == null) throw new HitException();
        token = token.trim();
        if (token.length() < 2) throw new HitException();
        
        char columnChar = token.charAt(0);
        columnChar = Character.toUpperCase(columnChar);
        if (columnChar < 'A' || columnChar > 'J') throw new HitException();
        int columnNum = columnChar - 'A';
        
        String rowPart = token.substring(1);
        int rowNum;
        try { 
        	rowNum = Integer.parseInt(rowPart);
        }
        catch (Exception e) {
        	throw new HitException();
        }
        
        if (rowNum < 1 || rowNum > 10) throw new HitException();
        
        rowNum = rowNum - 1;
        
        if (shot[rowNum][columnNum]) throw new HitException();
        return new int[] {rowNum, columnNum};
    }

    /**
     * Marks (row, col) as shot.
     * Prints "Miss" or "Hit X" (X = uppercase ship character).
     * Updates score by adding ship.size for a hit.
     */
    private void shoot(int row, int col) {
        shot[row][col] = true;
        if (shipRef[row][col] != null) {
        	score += shipRef[row][col].size;
        	shipRef[row][col].hits++;
        	char type = baseBoard[row][col];
        	System.out.println("Hit " + type);
        }
        else {
        	System.out.println("Miss");
        }
    }

    // === Display ===

    /**
     * Prints the current board state to stdout.
     *
     * Format:
     *   "  A B C D E F G H I J"
     *   "  - - - - - - - - - -"
     *   "1 | <cells...>"
     *   ...
     *   "10 | <cells...>"
     *
     * Cell rendering:
     *   - Not shot, empty  : " "
     *   - Not shot, ship   : ship character (uppercase)
     *   - Shot, empty      : "X"
     *   - Shot, ship       : "X" + ship character (lowercase)   e.g. "Xp", "Xa"
     *
     * Trailing spaces must be stripped from every line.
     */
    private void printBoard() {
        System.out.println("  A B C D E F G H I J");
        System.out.println("  - - - - - - - - - -");
        for (int i = 0; i < BOARD_SIZE; i++) {
        	String line = "";
        	line += (i+1) + " | ";
        	for (int j = 0; j < BOARD_SIZE; j++) {
        		if (!shot[i][j] && baseBoard[i][j] == ' ') {
        			line += " ";
        		}
        		else if (!shot[i][j]) {
        			line += baseBoard[i][j];
        		}
        		else if (baseBoard[i][j] == ' ') { 
        			line += "X";
        		}
        		else {
        			line += "X" + Character.toLowerCase(baseBoard[i][j]);
        		}
        		line += " ";
        	}
        	System.out.println(line.stripTrailing());
        }
    }

    // === Inner Types ===

    /** Execution mode. */
    private enum Mode { DEBUG, RELEASE }

    /** Holds parsed startup parameters. */
    private static class StartupConfig {
        final int    bombs;
        final Mode   mode;
        final String fileName;

        StartupConfig(int bombs, Mode mode, String fileName) {
            this.bombs    = bombs;
            this.mode     = mode;
            this.fileName = fileName;
        }
    }

    // === Ship Hierarchy ===

    /**
     * Abstract base class for all ship types.
     * Fields:
     *   type -- single uppercase character identifying the ship (A, B, S, D, P)
     *   size -- number of cells the ship occupies
     *   hits -- number of times this ship has been hit (optional to use)
     */
    private abstract static class Ship {
        final char type;
        final int  size;
        int        hits;

        Ship(char type, int size) {
            this.type = type;
            this.size = size;
            this.hits = 0;
        }
    }

    /** Aircraft Carrier: type='A', size=6, count=1 */
    private static final class AircraftCarrier extends Ship {
        AircraftCarrier() { super('A', 6); }
    }

    /** Battleship: type='B', size=4, count=2 */
    private static final class BattleshipShip extends Ship {
        BattleshipShip() { super('B', 4); }
    }

    /** Submarine: type='S', size=3, count=2 */
    private static final class Submarine extends Ship {
        Submarine() { super('S', 3); }
    }

    /** Destroyer: type='D', size=3, count=1 */
    private static final class Destroyer extends Ship {
        Destroyer() { super('D', 3); }
    }

    /** Patrol Boat: type='P', size=2, count=4 */
    private static final class PatrolBoat extends Ship {
        PatrolBoat() { super('P', 2); }
    }

    // === Exceptions ===

    /** Thrown when N is not a positive integer. */
    private static class BombInputException extends Exception {
        private static final long serialVersionUID = 1L;
    }

    /** Thrown when MODE is not d, D, r, or R. */
    private static class ModeInputException extends Exception {
        private static final long serialVersionUID = 1L;
    }

    /**
     * Thrown when a coordinate is already shot, out of range, or malformed.
     * Caught in the game loop; prints "Try again" without consuming a bomb.
     */
    private static class HitException extends Exception {
        private static final long serialVersionUID = 1L;
    }
}
