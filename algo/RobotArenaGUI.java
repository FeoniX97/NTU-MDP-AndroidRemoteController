import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;
import javax.swing.Timer;

public class RobotArenaGUI {
    private static final int GRID_SIZE = 50;
    private static final int CELL_SIZE = 13;
    private static final int MARGIN = 50;
    private static final int OBSTACLE_COUNT = 5;
    //private static final int BARRIER_COUNT = 2;
    private static final int ROBOT_SIZE = 3;

    private JFrame frame;
    private ArenaPanel arenaPanel;
    private JTextArea infoArea;
    private JTextField timeField;
    private JButton startButton;
    private Timer timer;

    //obs
    private Object[][] obstacles;

    //private Set<Point> barrierSet;
    private int robotX = 0;
    private int robotY = 0;
    private String robotDirection = "up";
    private int currentTarget = 0;
    private int stoppingTime = 0;

    private List<Point> path;
    private int currentPathIndex = 0;
    private List<Point> plannedPath = new ArrayList<>();

    //method to set
    public void setPlannedPath(List<Point> path) {
        this.plannedPath = path;
        arenaPanel.repaint(); // Call repaint on the arenaPanel
    }

    public RobotArenaGUI() {
        frame = new JFrame("GUI");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout());

        setFixedObstacles();
        //setFixedBarriers();

        arenaPanel = new ArenaPanel();
        arenaPanel.setPreferredSize(new Dimension(GRID_SIZE * CELL_SIZE + 2 * MARGIN, GRID_SIZE * CELL_SIZE + 2 * MARGIN));
        frame.add(arenaPanel, BorderLayout.CENTER);

        infoArea = new JTextArea(10, 30);
        infoArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(infoArea);
        frame.add(scrollPane, BorderLayout.EAST);

        JPanel controlPanel = new JPanel();
        timeField = new JTextField(5);
        startButton = new JButton("Start");
        controlPanel.add(new JLabel("Time Limit: "));
        controlPanel.add(timeField);
        controlPanel.add(startButton);
        frame.add(controlPanel, BorderLayout.SOUTH);

        startButton.addActionListener(e -> startRobot());

        updateObstacleInfo();

        frame.pack();
        frame.setVisible(true);
    }

    //updated point
    private void setFixedObstacles() {
        obstacles = new Object[OBSTACLE_COUNT][4];
        obstacles[0] = new Object[]{10, 37, "up", "A"}; //up: y+2
        obstacles[1] = new Object[]{20, 33, "down", "B"}; //down: y-2
        obstacles[2] = new Object[]{30, 20, "up", "C"}; //left: x-2
        obstacles[3] = new Object[]{43, 10, "left", "D"}; //right: x+2
        obstacles[4] = new Object[]{25, 32, "up", "E"}; //up: y+2
    }
    /* 
    private void setFixedBarriers() {
        barrierSet = new HashSet<>();
        addBarrier(5, 43, 3, 2);
        addBarrier(35, 11, 2, 4);
    }

    private void addBarrier(int x, int y, int width, int height) {
        for (int i = x; i < x + width; i++) {
            for (int j = y; j < y + height; j++) {
                barrierSet.add(new Point(i, j));
            }
        }
    }*/

    private void updateObstacleInfo() {
        StringBuilder sb = new StringBuilder();
        for (Object[] obstacle : obstacles) {
            int x = (int) obstacle[0];
            int y = (int) obstacle[1];
            String direction = (String) obstacle[2];
            String name = (String) obstacle[3];
            sb.append(String.format("target %s: (%d, %d, %s)%n", name, x, y, direction));
        }
        sb.append("\nDetected Obstacles' Information:\n");
        /* 
        int barrierCount = 1;
        for (Point barrier : barrierSet) {
            sb.append(String.format("obs %d: (%d, %d)%n", barrierCount++, barrier.x, barrier.y));
        }*/
        infoArea.setText(sb.toString());
    }

    private void startRobot() {
        try {
            int timeLimit = Integer.parseInt(timeField.getText());
            startButton.setEnabled(false);
            timeField.setEnabled(false);

            calculatePath();

            timer = new Timer(300, new ActionListener() {
                int remainingTime = timeLimit * 10;

                @Override
                public void actionPerformed(ActionEvent e) {
                    if (remainingTime > 0) {
                        if (stoppingTime > 0) {
                            stoppingTime--;
                            if (stoppingTime == 0) {
                                currentTarget++;
                                if (currentTarget >= OBSTACLE_COUNT) {
                                    ((Timer)e.getSource()).stop();
                                    updateInfoArea("All image scanning completed. Robot stop.");
                                    startButton.setEnabled(true);
                                    timeField.setEnabled(true);
                                    return;
                                }
                            }
                        } else {
                            moveRobot();
                        }
                        if (remainingTime % 10 == 0) {
                            updateInfoArea("Remaining time: " + (remainingTime / 10) + " s");
                        }
                        remainingTime--;
                    } else {
                        ((Timer)e.getSource()).stop();
                        updateInfoArea("Time's Up !");
                        startButton.setEnabled(true);
                        timeField.setEnabled(true);
                    }
                }
            });
            timer.start();
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(frame, "Please enter a valid time", "Invalid input !", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void calculatePath() {
        List<Point> targets = new ArrayList<>();
        targets.add(new Point(robotX, robotY)); // Starting point
        for (Object[] obstacle : obstacles) {
            int x = (int) obstacle[0];
            int y = (int) obstacle[1];
            String direction = (String) obstacle[2];
            switch (direction) {
                case "up": y += ROBOT_SIZE; break;
                case "down": y -= ROBOT_SIZE; break;
                case "left": x -= ROBOT_SIZE; break; 
                case "right": x += ROBOT_SIZE; break; 
            }
            targets.add(new Point(x, y));
        }

        List<Point> optimizedRoute = nearestNeighborTSP(targets);
        path = findPathThroughAllPoints(optimizedRoute);
        currentPathIndex = 0;
    }

    private List<Point> nearestNeighborTSP(List<Point> points) {
        List<Point> route = new ArrayList<>();
        Set<Point> unvisited = new HashSet<>(points);

        Point current = points.get(0); // Start from the robot's initial position
        route.add(current);
        unvisited.remove(current);

        while (!unvisited.isEmpty()) {
            Point nearest = null;
            double minDistance = Double.MAX_VALUE;

            for (Point point : unvisited) {
                double distance = distance(current, point);
                if (distance < minDistance) {
                    minDistance = distance;
                    nearest = point;
                }
            }

            route.add(nearest);
            unvisited.remove(nearest);
            current = nearest;
        }

        return route;
    }

    private double distance(Point a, Point b) {
        return Math.sqrt(Math.pow(a.x - b.x, 2) + Math.pow(a.y - b.y, 2));
    }

    private List<Point> findPathThroughAllPoints(List<Point> points) {
        List<Point> fullPath = new ArrayList<>();
        for (int i = 0; i < points.size() - 1; i++) {
            List<Point> partialPath = aStar(points.get(i), points.get(i + 1));
            if (i > 0) {
                partialPath.remove(0); // Remove duplicate point
            }
            fullPath.addAll(partialPath);
        }
        return fullPath;
    }

    private List<Point> aStar(Point start, Point goal) {
        PriorityQueue<Node> openSet = new PriorityQueue<>();
        Set<Point> closedSet = new HashSet<>();
        Map<Point, Node> allNodes = new HashMap<>();

        Node startNode = new Node(start, null, 0, heuristic(start, goal));
        openSet.offer(startNode);
        allNodes.put(start, startNode);

        while (!openSet.isEmpty()) {
            Node current = openSet.poll();
            if (current.point.equals(goal)) {
                return reconstructPath(current);
            }

            closedSet.add(current.point);

            for (Point neighbor : getNeighbors(current.point)) {
                if (closedSet.contains(neighbor)) {
                    continue;
                }

                double tentativeGScore = current.gScore + 1;

                Node neighborNode = allNodes.get(neighbor);
                if (neighborNode == null) {
                    neighborNode = new Node(neighbor, current, tentativeGScore, heuristic(neighbor, goal));
                    allNodes.put(neighbor, neighborNode);
                    openSet.offer(neighborNode);
                } else if (tentativeGScore < neighborNode.gScore) {
                    neighborNode.parent = current;
                    neighborNode.gScore = tentativeGScore;
                    neighborNode.fScore = neighborNode.gScore + neighborNode.hScore;
                    openSet.remove(neighborNode);
                    openSet.offer(neighborNode);
                }
            }
        }

        return new ArrayList<>(); // No path found
    }

    private double heuristic(Point a, Point b) {
        return Math.abs(a.x - b.x) + Math.abs(a.y - b.y);
    }

    private List<Point> getNeighbors(Point p) {
        List<Point> neighbors = new ArrayList<>();
        int[][] directions = {{0, 1}, {1, 0}, {0, -1}, {-1, 0}};
        for (int[] dir : directions) {
            Point newPoint = new Point(p.x + dir[0], p.y + dir[1]);
            if (isValidPosition(newPoint.x, newPoint.y)) {
                neighbors.add(newPoint);
            }
        }
        return neighbors;
    }

    private List<Point> reconstructPath(Node endNode) {
        List<Point> path = new ArrayList<>();
        for (Node node = endNode; node != null; node = node.parent) {
            path.add(0, node.point);
        }
        return path;
    }

    private boolean isValidPosition(int x, int y) {
        if (x < 0 || x + ROBOT_SIZE > GRID_SIZE || y < 0 || y + ROBOT_SIZE > GRID_SIZE) {
            return false;
        }
        for (int i = x; i < x + ROBOT_SIZE; i++) {
            for (int j = y; j < y + ROBOT_SIZE; j++) {
                /* 
                if (barrierSet.contains(new Point(i, j))) {
                    return false;
                }*/
                for (Object[] obstacle : obstacles) {
                    int ox = (int) obstacle[0];
                    int oy = (int) obstacle[1];
                    if (i >= ox && i < ox + ROBOT_SIZE && j >= oy && j < oy + ROBOT_SIZE) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private String getDirectionToTarget(int startX, int startY, int endX, int endY) {
        if (startX < endX) return "right";
        if (startX > endX) return "left";
        if (startY < endY) return "up";
        if (startY > endY) return "down";
        return robotDirection; // No change if on the same spot
    }

    private String getDirectionName(String direction) {
        switch (direction) {
            case "up": return "   North ↑ ↑  ";
            case "down": return "   South ↓ ↓ ";
            case "left": return "   West ← ←  ";
            case "right": return "   East →  → ";
            default: return direction;
        }
    }

    private void moveRobot() {
        if (currentPathIndex < path.size()) {
            Point target = path.get(currentPathIndex);

            // Check if the robot needs to turn
            String newDirection = getDirectionToTarget(robotX, robotY, target.x, target.y);
            if (!newDirection.equals(robotDirection)) {
                updateInfoArea("Turn to " + getDirectionName(newDirection) + " direction");
                robotDirection = newDirection;
            }

            robotX = target.x;
            robotY = target.y;
            currentPathIndex++;

            for (int i = 0; i < obstacles.length; i++) {
                Object[] obstacle = obstacles[i];
                int targetX = (int) obstacle[0];
                int targetY = (int) obstacle[1];
                String targetDirection = (String) obstacle[2];
                String targetName = (String) obstacle[3];

                int actualTargetX = targetX;
                int actualTargetY = targetY;
                switch (targetDirection) {
                    case "up":actualTargetY += ROBOT_SIZE; break;
                    case "down": actualTargetY -= ROBOT_SIZE; break;
                    case "left": actualTargetX -= ROBOT_SIZE; break;
                    case "right": actualTargetX += ROBOT_SIZE; break;
                }

                if (robotX == actualTargetX && robotY == actualTargetY) {
                    // robot direction set to same as opp ****
                    robotDirection = getOppositeDirection(targetDirection);
                    updateInfoArea("Turn to" + getDirectionName(robotDirection) + " direction");
                    updateInfoArea("Reached Target " + targetName + ", Scanning....");
                    stoppingTime = 30; // 3 seconds (30 * 100ms)

                    break;
                }
            }

            arenaPanel.repaint();
            updateInfoArea("current robot at :  (" + robotX + ", " + robotY + ")");
        }
    }

    private String getOppositeDirection(String direction) {
        switch (direction) {
            case "up": return "down";
            case "down": return "up";
            case "left": return "right";
            case "right": return "left";
            default: return direction;
        }
    }

    private void updateInfoArea(String message) {
        infoArea.append(message + "\n");
        infoArea.setCaretPosition(infoArea.getDocument().getLength());
    }

    class Node implements Comparable<Node> {
        Point point;
        Node parent;
        double gScore;
        double hScore;
        double fScore;

        Node(Point point, Node parent, double gScore, double hScore) {
            this.point = point;
            this.parent = parent;
            this.gScore = gScore;
            this.hScore = hScore;
            this.fScore = gScore + hScore;
        }

        @Override
        public int compareTo(Node other) {
            return Double.compare(this.fScore, other.fScore);}
    }

    class ArenaPanel extends JPanel {
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2d = (Graphics2D) g;

            // Draw grid
            g2d.setColor(Color.LIGHT_GRAY);
            for (int i = 0; i <= GRID_SIZE; i++) {
                g2d.drawLine(MARGIN + i * CELL_SIZE, MARGIN, MARGIN + i * CELL_SIZE, MARGIN + GRID_SIZE * CELL_SIZE);
                g2d.drawLine(MARGIN, MARGIN + i * CELL_SIZE, MARGIN + GRID_SIZE * CELL_SIZE, MARGIN + i * CELL_SIZE);
            }

            // Draw planned path
            if (!plannedPath.isEmpty()) {
                g2d.setColor(Color.GREEN);
                g2d.setStroke(new BasicStroke(2)); // line weight

                for (int i = 0; i < plannedPath.size() - 1; i++) {
                    Point start = plannedPath.get(i);
                    Point end = plannedPath.get(i + 1);

                    int startX = MARGIN + (start.x + ROBOT_SIZE / 2) * CELL_SIZE;
                    int startY = MARGIN + (GRID_SIZE - start.y - ROBOT_SIZE / 2) * CELL_SIZE;
                    int endX = MARGIN + (end.x + ROBOT_SIZE / 2) * CELL_SIZE;
                    int endY = MARGIN + (GRID_SIZE - end.y - ROBOT_SIZE / 2) * CELL_SIZE;

                    g2d.drawLine(startX, startY, endX, endY);
                }
            }


            // Draw obstacles with direction arrows and letters
            g2d.setColor(Color.RED);
            for (Object[] obstacle : obstacles) {
                int x = (int) obstacle[0];
                int y = (int) obstacle[1];
                String direction = (String) obstacle[2];
                String letter = (String) obstacle[3];
                g2d.fillRect(MARGIN + x * CELL_SIZE, MARGIN + (GRID_SIZE - y - ROBOT_SIZE) * CELL_SIZE, ROBOT_SIZE * CELL_SIZE, ROBOT_SIZE * CELL_SIZE);

                // Draw direction arrow for obstacle
                g2d.setColor(Color.WHITE);
                int arrowSize = CELL_SIZE;
                int arrowX = MARGIN + (x + ROBOT_SIZE / 2) * CELL_SIZE;
                int arrowY = MARGIN + (GRID_SIZE - y - ROBOT_SIZE / 2) * CELL_SIZE;
                drawArrow(g2d, arrowX, arrowY, direction, arrowSize);

                // Draw letter for obstacle in black within the red square
                g2d.setColor(Color.BLACK);
                g2d.setFont(new Font("Arial", Font.BOLD, 12));
                FontMetrics fm = g2d.getFontMetrics();
                int letterWidth = fm.stringWidth(letter);
                int letterHeight = fm.getHeight();

                int letterX, letterY;/* */
                switch (direction) {
                    case "up":
                        letterX = MARGIN + (x + ROBOT_SIZE / 2) * CELL_SIZE;
                        letterY = MARGIN + (GRID_SIZE - y - ROBOT_SIZE + 1) * CELL_SIZE;
                        break;
                    case "down":
                        letterX = MARGIN + (x + ROBOT_SIZE / 2) * CELL_SIZE;
                        letterY = MARGIN + (GRID_SIZE - y - 1) * CELL_SIZE;
                        break;
                    case "left":
                        letterX = MARGIN + (x + ROBOT_SIZE - 1) * CELL_SIZE;
                        letterY = MARGIN + (GRID_SIZE - y - ROBOT_SIZE / 2) * CELL_SIZE;
                        break;
                    case "right":
                        letterX = MARGIN + (x + 1) * CELL_SIZE;
                        letterY = MARGIN + (GRID_SIZE - y - ROBOT_SIZE / 2) * CELL_SIZE;
                        break;
                    default:
                        letterX = arrowX;
                        letterY = arrowY;
                }
                g2d.drawString(letter, letterX - letterWidth / 2, letterY + letterHeight / 4);
                g2d.setColor(Color.RED);
            }

            // Draw barriers
            /* 
            g2d.setColor(Color.BLACK);
            for (Point barrier : barrierSet) {
                g2d.fillRect(MARGIN + barrier.x * CELL_SIZE, MARGIN + (GRID_SIZE - barrier.y - 1) * CELL_SIZE, CELL_SIZE, CELL_SIZE);
            }*/

            // Draw robot
            g2d.setColor(Color.BLUE);
            g2d.fillRect(MARGIN + robotX * CELL_SIZE, MARGIN + (GRID_SIZE - robotY - ROBOT_SIZE) * CELL_SIZE, ROBOT_SIZE * CELL_SIZE, ROBOT_SIZE * CELL_SIZE);

            // Draw robot direction indicator
            g2d.setColor(Color.WHITE);
            int arrowSize = CELL_SIZE;
            int centerX = MARGIN + (robotX + ROBOT_SIZE / 2) * CELL_SIZE;
            int centerY = MARGIN + (GRID_SIZE - robotY - ROBOT_SIZE / 2) * CELL_SIZE;
            drawArrow(g2d, centerX, centerY, robotDirection, arrowSize);

            // Draw coordinate labels
            g2d.setColor(Color.BLACK);
            for (int i = 0; i <= GRID_SIZE; i += 5) {
                g2d.drawString(Integer.toString(i), MARGIN + i * CELL_SIZE, MARGIN - 5);
                g2d.drawString(Integer.toString(i), MARGIN - 30, MARGIN + (GRID_SIZE - i) * CELL_SIZE);
            }
        }

        private void drawArrow(Graphics2D g2d, int centerX, int centerY, String direction, int arrowSize) {
            switch (direction) {
                case "up":
                    g2d.fillPolygon(new int[]{centerX, centerX - arrowSize / 2, centerX + arrowSize / 2},
                            new int[]{centerY - arrowSize / 2, centerY + arrowSize / 2, centerY + arrowSize / 2}, 3);
                    break;
                case "down":
                    g2d.fillPolygon(new int[]{centerX, centerX - arrowSize / 2, centerX + arrowSize / 2},
                            new int[]{centerY + arrowSize / 2, centerY - arrowSize / 2, centerY - arrowSize / 2}, 3);
                    break;
                case "left":
                    g2d.fillPolygon(new int[]{centerX - arrowSize / 2, centerX + arrowSize / 2, centerX + arrowSize / 2},
                            new int[]{centerY, centerY - arrowSize / 2, centerY + arrowSize / 2}, 3);
                    break;
                case "right":
                    g2d.fillPolygon(new int[]{centerX + arrowSize / 2, centerX - arrowSize / 2, centerX - arrowSize / 2},
                            new int[]{centerY, centerY - arrowSize / 2, centerY + arrowSize / 2}, 3);
                    break;
            }
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(RobotArenaGUI::new);
    }
}