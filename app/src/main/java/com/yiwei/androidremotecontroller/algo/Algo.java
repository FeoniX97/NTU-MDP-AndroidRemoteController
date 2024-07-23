package com.yiwei.androidremotecontroller.algo;

import android.graphics.Point;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;
import java.util.*;

public class Algo {
    private static final int GRID_SIZE = 50;
    private static final int CELL_SIZE = 13;
    private static final int MARGIN = 50;
    private static final int ROBOT_SIZE = 3;

    private static int OBSTACLE_COUNT = 0;
    private static Algo instance = new Algo();

    // obs
    private Object[][] obstacles;
    private int currentTarget = 0;
    private int addObstacleIdx = 0;

    private int robotX = 0;
    private int robotY = 0;
    private String robotDirection = "up";

    private List<Point> path;
    private int currentPathIndex = 0;

    private JSONObject pathObj = new JSONObject();

    public static Algo setObstacleSize(int size) {
        if (size < 1) {
            throw new RuntimeException("Obstacle size invalid!");
        }

        instance.currentTarget = instance.addObstacleIdx = instance.currentPathIndex = 0;
        instance.robotDirection = "up";
        instance.robotX = instance.robotY = 0;
        OBSTACLE_COUNT = size;

        instance.pathObj = new JSONObject();
        try {
            instance.pathObj.put("path", new JSONArray());
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }

        instance.obstacles = new Object[OBSTACLE_COUNT][4];

        return instance;
    }

    public Algo addObstacle(Point axis, String dir, int obstacleId) {
        if (OBSTACLE_COUNT == 0) {
            throw new RuntimeException("Obstacle size must be set first!");
        }

        // dir: up, down, left, right
        obstacles[addObstacleIdx++] = new Object[] { axis.x, axis.y, dir, obstacleId }; // up: y+2

        return instance;
    }

    public JSONObject buildPath() throws JSONException {
        Log.e("algo", "building algo path ...");

        calculatePath();

        for (; currentPathIndex < path.size() && currentTarget < OBSTACLE_COUNT; currentPathIndex++) {
            moveRobot();
        }

        return pathObj;
    }

    private void moveRobot() throws JSONException {
        JSONArray pathArr = pathObj.getJSONArray("path");

        Point target = path.get(currentPathIndex);

        // Check if the robot needs to turn
        String newDirection = getDirectionToTarget(robotX, robotY, target.x, target.y);
        if (!newDirection.equals(robotDirection)) {
            // Log.e("algo","Turn to " + getDirectionName(newDirection) + " direction");
            robotDirection = newDirection;
            JSONObject jsonObj = new JSONObject();
            jsonObj.put("d", newDirection);
            jsonObj.put("s", -1);
            jsonObj.put("x", robotX);
            jsonObj.put("y", robotY);
            pathArr.put(jsonObj);
        }

        robotX = target.x;
        robotY = target.y;

        for (int i = 0; i < obstacles.length; i++) {
            Object[] obstacle = obstacles[i];
            int targetX = (int) obstacle[0];
            int targetY = (int) obstacle[1];
            String targetDirection = (String) obstacle[2];
            int targetId = (int) obstacle[3];

            int actualTargetX = targetX;
            int actualTargetY = targetY;
            switch (targetDirection) {
                case "up":
                    actualTargetY += ROBOT_SIZE;
                    break;
                case "down":
                    actualTargetY -= ROBOT_SIZE;
                    break;
                case "left":
                    actualTargetX -= ROBOT_SIZE;
                    break;
                case "right":
                    actualTargetX += ROBOT_SIZE;
                    break;
            }

            if (robotX == actualTargetX && robotY == actualTargetY) {
                // robot direction set to same as opp ****
                robotDirection = getOppositeDirection(targetDirection);

                JSONObject jsonObj = new JSONObject();
                jsonObj.put("d", robotDirection);
                jsonObj.put("s", targetId);
                jsonObj.put("x", robotX);
                jsonObj.put("y", robotY);
                pathArr.put(jsonObj);

                // Log.e("algo","Turn to" + getDirectionName(robotDirection) + " direction");
                // Log.e("algo","Reached Target " + targetId + ", Scanning....");

                currentTarget++;

                break;
            }
        }

        JSONObject jsonObj = new JSONObject();
        jsonObj.put("d", robotDirection);
        jsonObj.put("s", -1);
        jsonObj.put("x", robotX);
        jsonObj.put("y", robotY);
        pathArr.put(jsonObj);
        // Log.e("algo","current robot at :  (" + robotX + ", " + robotY + ")");
    }

    private String getOppositeDirection(String direction) {
        switch (direction) {
            case "up":
                return "down";
            case "down":
                return "up";
            case "left":
                return "right";
            case "right":
                return "left";
            default:
                return direction;
        }
    }

    private String getDirectionName(String direction) {
        switch (direction) {
            case "up":
                return "   North ↑ ↑  ";
            case "down":
                return "   South ↓ ↓ ";
            case "left":
                return "   West ← ←  ";
            case "right":
                return "   East →  → ";
            default:
                return direction;
        }
    }

    private String getDirectionToTarget(int startX, int startY, int endX, int endY) {
        if (startX < endX)
            return "right";
        if (startX > endX)
            return "left";
        if (startY < endY)
            return "up";
        if (startY > endY)
            return "down";
        return robotDirection; // No change if on the same spot
    }

    // updated point
    private void setFixedObstacles() {
        obstacles = new Object[OBSTACLE_COUNT][4];
        obstacles[0] = new Object[] { 10, 37, "up", "A" }; // up: y+2
        obstacles[1] = new Object[] { 20, 33, "down", "B" }; // down: y-2
        obstacles[2] = new Object[] { 30, 20, "up", "C" }; // left: x-2
        obstacles[3] = new Object[] { 43, 10, "left", "D" }; // right: x+2
        obstacles[4] = new Object[] { 25, 32, "up", "E" }; // up: y+2
    }

    private void calculatePath() {
        List<Point> targets = new ArrayList<>();
        targets.add(new Point(robotX, robotY)); // Starting point
        for (Object[] obstacle : obstacles) {
            int x = (int) obstacle[0];
            int y = (int) obstacle[1];
            String direction = (String) obstacle[2];
            switch (direction) {
                case "up":
                    y += ROBOT_SIZE;
                    break;
                case "down":
                    y -= ROBOT_SIZE;
                    break;
                case "left":
                    x -= ROBOT_SIZE;
                    break;
                case "right":
                    x += ROBOT_SIZE;
                    break;
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

    private List<Point> getNeighbors(Point p) {
        List<Point> neighbors = new ArrayList<>();
        int[][] directions = { { 0, 1 }, { 1, 0 }, { 0, -1 }, { -1, 0 } };
        for (int[] dir : directions) {
            Point newPoint = new Point(p.x + dir[0], p.y + dir[1]);
            if (isValidPosition(newPoint.x, newPoint.y)) {
                neighbors.add(newPoint);
            }
        }
        return neighbors;
    }

    private boolean isValidPosition(int x, int y) {
        if (x < 0 || x + ROBOT_SIZE > GRID_SIZE || y < 0 || y + ROBOT_SIZE > GRID_SIZE) {
            return false;
        }
        for (int i = x; i < x + ROBOT_SIZE; i++) {
            for (int j = y; j < y + ROBOT_SIZE; j++) {
                /*
                 * if (barrierSet.contains(new Point(i, j))) {
                 * return false;
                 * }
                 */
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

    private List<Point> reconstructPath(Node endNode) {
        List<Point> path = new ArrayList<>();
        for (Node node = endNode; node != null; node = node.parent) {
            path.add(0, node.point);
        }
        return path;
    }

    private double heuristic(Point a, Point b) {
        return Math.abs(a.x - b.x) + Math.abs(a.y - b.y);
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
            return Double.compare(this.fScore, other.fScore);
        }
    }

    public static Point getAlgoPointFromArenaPoint(int arenaX, int arenaY) {
        return new Point(arenaX - 1, arenaY - 1);
    }

    public static Point getArenaPointFromAlgoPoint(int algoX, int algoY) {
        return new Point(algoX + 1, algoY + 1);
    }

}
