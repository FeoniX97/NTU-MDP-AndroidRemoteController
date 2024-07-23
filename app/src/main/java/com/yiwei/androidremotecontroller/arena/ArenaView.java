package com.yiwei.androidremotecontroller.arena;

import static android.content.Context.MODE_PRIVATE;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.CountDownTimer;
import android.os.Handler;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.yiwei.androidremotecontroller.MainActivity;
import com.yiwei.androidremotecontroller.R;
import com.yiwei.androidremotecontroller.algo.Algo;
import com.yiwei.androidremotecontroller.algo.ApiTask;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ArenaView extends RelativeLayout {

    // constants
    public static Point COG = new Point(1, -1); // center of gravity, the x&y axis offsets from top left point, for this case its center
    public static int OBSTACLE_SIZE = 3; // 3x3
    public static int ROWS = 50;

    private int COLS = 50;
    private final int LEFT_OFFSET = 20;
    public static final int MARGIN = 2;
    public static int TILE_SIZE = 15;
    private final int ROBOT_SIZE = 3; // 3x3
    private Point ROBOT_START_AXIS;
    // end of constants

    // paint brushes
    private final Paint mBlackPaintBrushFill;
    private final Paint mBluePaintBrushFill;
    private final Paint mRedPaintBrushFill;

    // background drawings
    private final Rect[][] mRects = new Rect[ROWS][COLS];
    private final Point[] mCoordsY = new Point[ROWS];
    private final Point[] mCoordsX = new Point[COLS];

    // tiles and robot, obstacle is stored in individual tile object
    private final ArenaTileView[][] mTiles = new ArenaTileView[ROWS][COLS];
    private RobotView mRobot;
    private int obstacleSpawned = 0;

    public MainActivity mainActivity;
    public ArenaPathLineView arenaPathLineView;
    private ApiTask apiTask;

    public Handler moveRobotHandler;
    public Handler timerHandler;
    private Runnable timerRunnable;
    public CountUpTimer timer;
    private Runnable moveRobotRunnable;
    public JSONArray calculatedPath;
    public JSONArray calculatedCommands;

    public ArenaView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);

        setWillNotDraw(false);

        mBlackPaintBrushFill = new Paint();
        mBlackPaintBrushFill.setColor(Color.BLACK);
        mBlackPaintBrushFill.setStyle(Paint.Style.FILL);
        mBlackPaintBrushFill.setTextSize(12f);
        mBlackPaintBrushFill.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));

        mBluePaintBrushFill = new Paint();
        mBluePaintBrushFill.setColor(Color.GRAY);
        mBluePaintBrushFill.setStyle(Paint.Style.FILL);

        mRedPaintBrushFill = new Paint();
        mRedPaintBrushFill.setColor(Color.RED);
        mRedPaintBrushFill.setStyle(Paint.Style.FILL);
    }

    public void onMainActivityProvided() {
        String algoType = getCurrentAlgoType();

        Log.e("AlgoType", "Current algo type: " + algoType);

        // change grid size, obstacle size and basis axis based on algo type
        if (algoType.equals(MainActivity.AlgoType.SQ.name())) {
            // center of gravity, the x&y axis offsets from top left point, for this case its bottom left
            COG = new Point(0, -2);
            OBSTACLE_SIZE = 3;
            ROWS = COLS = 50;
            TILE_SIZE = 15;
            ROBOT_START_AXIS = new Point(0, 0);
        } else if (algoType.equals(MainActivity.AlgoType.EX.name())) {
            // center of gravity, the x&y axis offsets from top left point, for this case its center
            COG = new Point(1, -1);
            // OBSTACLE_SIZE = 1;
            OBSTACLE_SIZE = 3;
            ROWS = COLS = 50;
            TILE_SIZE = 15;
            ROBOT_START_AXIS = new Point(1, 1);
        }

        for (int m = 0; m < ROWS; m++) {
            for (int n = 0; n < COLS; n++) {
                int left = n * TILE_SIZE + MARGIN + LEFT_OFFSET;
                int top = m * TILE_SIZE + MARGIN;
                int right = n * TILE_SIZE + TILE_SIZE + LEFT_OFFSET;
                int bottom = m * TILE_SIZE + TILE_SIZE;

                mRects[m][n] = new Rect();
                mRects[m][n].set(left, top, right, bottom);

                // calculate whether the new tile should be COG
                boolean isCOG = (n % (ROBOT_SIZE) == 0);

                // add tile view to rect
                // stretch some offset height for most bottom rows
                int offset = (m == ROWS - 1) ? 4 : 0;
                RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(TILE_SIZE - MARGIN, TILE_SIZE - MARGIN + offset);
                params.leftMargin = left;
                params.topMargin = top;
                ArenaTileView tileView = new ArenaTileView(getContext(), left, top, n, m, TILE_SIZE, isCOG, this);
                addView(tileView, params);

                // save tile to array
                mTiles[m][n] = tileView;

                // save coordinates X
                if (m == 0)
                    mCoordsX[n] = new Point(n * TILE_SIZE + TILE_SIZE * 2 - 8, TILE_SIZE * ROWS + MARGIN + 20);
            }

            // save coordinates Y
            mCoordsY[m] = new Point(MARGIN, m * TILE_SIZE + (MARGIN + TILE_SIZE / 2 + 2));
        }

        // spawn the pathLineView
        LayoutParams params = new LayoutParams(TILE_SIZE * ROWS - MARGIN, TILE_SIZE * COLS);
        ArenaTileView mostTopLeftTile = getTileFromIdx(0, 0);
        arenaPathLineView = new ArenaPathLineView(getContext(), mostTopLeftTile.getCoordX(), mostTopLeftTile.getCoordY(), this);
        addView(arenaPathLineView, params);

        // spawn robot at bottom left
        Point idxPoint = getIdxFromAxis(ROBOT_START_AXIS);
        updateRobotPosition(idxPoint.x, idxPoint.y, 0);

        // add some obstacles
        if (getStoredObstacles().isEmpty()) {
            addObstacle(getTileFromAxis(10, 37), 'n');
            addObstacle(getTileFromAxis(20, 33), 's');
            addObstacle(getTileFromAxis(30, 20), 'n');
            addObstacle(getTileFromAxis(43, 10), 'w');
            addObstacle(getTileFromAxis(25, 32), 'n');
        } else {
            for (String obsData : getStoredObstacles()) {
                String[] parts = obsData.split(",");
                ObstacleView obs = addObstacle(getTileFromIdx(Integer.parseInt(parts[1]), Integer.parseInt(parts[2])), parts[3].charAt(0));
                obs.setId(Integer.parseInt(parts[0]));
                obs.setImageTargetId(Integer.parseInt(parts[4]));
            }
        }

        invalidate();

        Log.e("ArenaView", getStoredObstacles().toString());
    }

    /** Draw the Arena background */
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        for (int m = 0; m < ROWS; m++) {
            for (int n = 0; n < COLS; n++) {
                canvas.drawRect(mRects[m][n], mBluePaintBrushFill);

                if (m == 0) {
                    // draw coordinates X, by 5 interval
                    Point point = mCoordsX[n];
                    if (n % 5 == 0)
                        canvas.drawText(String.valueOf(n), n < 9 ? point.x + 3 : point.x, point.y, mBlackPaintBrushFill);

                    // draw the last x-axis
                    if (n == COLS - 1)
                        canvas.drawText(String.valueOf(n + 1), point.x + TILE_SIZE, point.y, mBlackPaintBrushFill);
                }
            }

            // draw coordinates Y, by 5 interval
            Point point = mCoordsY[m];
            if ((ROWS - m - 1) % 5 == 0)
                canvas.drawText(String.valueOf(ROWS - m - 1), point.x, point.y, mBlackPaintBrushFill);
        }
    }

    /** On receive message from robot, passed from MainActivity */
    public void onMessage(String message, MainActivity mainActivity) {
        // handle special message for c10, to be deleted in future
//        if (message.toLowerCase().contains("robot")) {
//            int x = Integer.valueOf(message.split(",")[1]);
//            int y = Integer.valueOf(message.split(",")[2]);
//            char dir = message.split(",")[3].charAt(0);
//
//            updateRobotPosition(x, y, getIntFromDir(dir));
//
//            return;
//        }
        // end of handling

        // handle special message for c9, to be deleted in future
//        if (message.toLowerCase().contains("target")) {
//            int obstacleId = Integer.valueOf(message.split(",")[1]);
//            int targetId = Integer.valueOf(message.split(",")[2]);
//            char dir = message.split(",")[3].charAt(0);
//
//            Log.e("MainActivity", "detected image, obstacleId: " + obstacleId + ", targetId: " + targetId);
//
//            ArenaTileView tile = getTileFromObstacleId(obstacleId);
//
//            if (tile != null && tile.getObstacle() != null) {
//                tile.getObstacle().setImageTargetId(targetId);
//                tile.getObstacle().setImageDir(dir);
//            }
//
//            return;
//        }

        // handle special message for c4
//        if (message.toLowerCase().contains("status")) {
//            JSONObject msgObj;
//            try {
//                msgObj = new JSONObject(message);
//                String status = msgObj.getString("status");
//                mainActivity.mReadBuffer.setText(status + " (" + msgObj + ")");
//            } catch (JSONException e) {
//                mainActivity.mReadBuffer.setText(message);
//            }
//
//            return;
//        }

        JSONObject msgObj;
        try {
            msgObj = new JSONObject(message);
        } catch (JSONException e) {
            // display the raw message on status text
            mainActivity.mReadBuffer.setText(message);

            return;
        }

        // robotPosition
//        JSONArray robotPosition;
//        try {
//            robotPosition = msgObj.getJSONArray("robotPosition");
//            int x = robotPosition.getInt(0);
//            int y = robotPosition.getInt(1);
//            int dirInt = robotPosition.getInt(2);
//
//            // spawn robot or change robot location if coordinates changes
//            updateRobotPosition(x, y, dirInt);
//        } catch (JSONException ignored) {}
        JSONObject robot;
        try {
            robot = msgObj.getJSONObject("robot");
            int x = robot.getInt("x");
            int y = robot.getInt("y");
            String dirStr = robot.getString("dir");

            // spawn robot or change robot location if coordinates changes
            Point idxPoint = getIdxFromAxis(new Point(x, y));
            updateRobotPosition(idxPoint.x, idxPoint.y,  getIntFromDirStr(dirStr));
            mainActivity.mReadBuffer.setText("robot: x: " + x + ", y: " + y + ", dir: " + dirStr);
        } catch (JSONException ignored) {}

        // obstacle
//        JSONArray obstacle;
//        try {
//            obstacle = msgObj.getJSONArray("obstacle");
//            int x = obstacle.getInt(0);
//            int y = obstacle.getInt(1);
//            int flag = obstacle.getInt(2); // added: 1, removed: 0
//
//            Log.e("MainActivity", "obstacle: " + x + ", " + y + ", " + flag);
//
//            ArenaTileView tileView = mTiles[y][x];
//            if (flag == 1 && tileView != null && tileView.getObstacle() == null) {
//                // when flag == 1, spawn obstacle if not already exists
//                ObstacleView newObstacle = addObstacle(tileView);
//                newObstacle.mainActivity = this.mainActivity;
//            } else if (flag == 0 && tileView.getObstacle() != null) {
//                // when flag == 0, remove obstacle if exists
//                removeObstacle(tileView);
//            }
//        } catch (Exception e) {
//            Log.e("MainActivity", e.getMessage());
//        }
        JSONObject obstacle;
        try {
            obstacle = msgObj.getJSONObject("obstacle");
            int id = obstacle.getInt("id");
            JSONObject image = obstacle.getJSONObject("image");
            int imageId = image.getInt("id");

            ArenaTileView tile = getTileFromObstacleId(id);

            if (tile != null && tile.getObstacle() != null) {
                tile.getObstacle().setImageTargetId(imageId);

                // set robot location to infront of obstacle
                if (this.mainActivity.mainMenu != null && this.mainActivity.mainMenu.getItem(9).isChecked())
                    updateRobotPositionToObs(tile.getObstacle());
            }

            String imageDirStr = image.getString("d");
            if (tile != null && tile.getObstacle() != null) {
                tile.getObstacle().setImageDirFromStr(imageDirStr);
            }
        } catch (JSONException ignored) {}

        // BT
        JSONObject btObj;
        String btStatus = "";
        try {
            btObj = msgObj.getJSONObject("bt");
            String addr = btObj.getString("addr");
            this.mainActivity.storeConnectedDeviceInfo("", addr);
            btStatus = "addr: " + addr;

            String name = btObj.getString("name");
            btStatus += ", name: " + name;
        } catch (Exception ignored) {}

        if (!btStatus.isEmpty()) {
            mainActivity.mReadBuffer.setText("Robot Connected. " + btStatus);
        }

        // pathCompleted
        boolean pathCompleted;
        try {
            pathCompleted = msgObj.getBoolean("pathCompleted");
            if (pathCompleted) {
                Toast.makeText(mainActivity, "Robot path completed, stopped timer", Toast.LENGTH_LONG).show();
                // stop the timer
                stopTimer();
            }
        } catch (JSONException ignored) {}

        // currentPathIdx
        int currentPathIdx;
        try {
            currentPathIdx = msgObj.getInt("currentPathIdx");
            // if (this.calculatedPath != null && currentPathIdx > -1 && currentPathIdx < this.calculatedPath.length()) {
            //     JSONObject pathObj = this.calculatedPath.getJSONObject(currentPathIdx);
            //     int x = pathObj.getInt("x");
            //     int y = pathObj.getInt("y");
            //     String d = pathObj.getString("d");
            //     Point idxPoint = getIdxFromAxis(new Point(x, y));
            //     updateRobotPosition(idxPoint.x, idxPoint.y, getDirIntFromStrForRobot(d));
            
            // }

            JSONArray newPathArray = new JSONArray();
            for (int i = 0; i < this.calculatedPath.size(); i++) {
                newPathArray.put(this.calculatedPath.getJSONObject(i);
            }
            
            String currentCommand = this.calculatedCommands.getString(currentPathIdx, "");
            // JSONArray commandArrWithoutSnap = new JSONArray();
            for (int i = 0; i < this.calculatedCommands.size(); i++) {
                String command = this.calculatedCommands.getString(i, "");
                if (command.contains("SNAP") || currentCommand.contains("FIN")) {
                    addStringToJSONArr(i + 1, null, newPathArray);
                }
            }
            
            JSONObject pathObj = newPathArray.getJSONObject(currentPathIdx + 1);
            if (pathObj != null) {
               int x = pathObj.getInt("x");
               int y = pathObj.getInt("y");
               String d = pathObj.getString("d");
               Point idxPoint = getIdxFromAxis(new Point(x, y));
               updateRobotPosition(idxPoint.x, idxPoint.y, getDirIntFromStrForRobot(d));
            }
        } catch (JSONException ignored) {}

        // status
        String status;
        try {
            status = msgObj.getString("status");
            mainActivity.mReadBuffer.setText(status);
        } catch (Exception ignored) {}
    }

    public void addStringToJSONArr(int pos, String data, JSONArray jsonArr){
        for (int i = jsonArr.length(); i > pos; i--){
            jsonArr.put(i, jsonArr.get(i-1));
        }
        jsonArr.put(pos, data);
    }
    
    /** bind robot position to a specific obstacle, side must be set */
    private void updateRobotPositionToObs(ObstacleView obstacle) {
        if (obstacle.getImageDir() == 'x') return;

        Point obsAxisPoint = obstacle.getAxisFromIdx();

        ArenaTileView tile;
        switch (obstacle.getImageDir()) {
            case 'n':
                tile = getTileFromAxis(obsAxisPoint.x, obsAxisPoint.y + 4);
                updateRobotPosition(tile.getIdxX(), tile.getIdxY(), 180); // robot face down
                break;
            case 'e':
                tile = getTileFromAxis(obsAxisPoint.x + 4, obsAxisPoint.y);
                updateRobotPosition(tile.getIdxX(), tile.getIdxY(), 270); // robot face down
                break;
            case 's':
                tile = getTileFromAxis(obsAxisPoint.x, obsAxisPoint.y - 4);
                updateRobotPosition(tile.getIdxX(), tile.getIdxY(), 0); // robot face down
                break;
            case 'w':
                tile = getTileFromAxis(obsAxisPoint.x - 4, obsAxisPoint.y);
                updateRobotPosition(tile.getIdxX(), tile.getIdxY(), 90); // robot face down
                break;
        }
    }

    /** x&y are idx!!! */
    public void updateRobotPosition(int x, int y, int dirInt) {
        if (mRobot == null || mRobot.getIdxX() != x || mRobot.getIdxY() != y) {
            Log.e("MainActivity", "new robot coord: " + x + ", " + y);

            // display the new robot coordinates in textbox
            try {
                Point axisPoint = getAxisFromIdx(new Point(x, y));
                ((EditText) mainActivity.findViewById(R.id.tb_coord)).setText(axisPoint.x + ", " + axisPoint.y);
            } catch (Exception ignored) {}

            // spawn and display the robot on arena, delete the old one if already exists
            if (mRobot != null) {
                removeView(mRobot);
                mRobot = null;
            }

            ArenaTileView COGTile = mTiles[y][x];
            ArenaTileView topLeftTile = getTopLeftTileFromCOGTile(COGTile);

            if (topLeftTile != null) {
                // RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(TILE_SIZE * OBSTACLE_SIZE - MARGIN, TILE_SIZE * OBSTACLE_SIZE - MARGIN);
                RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(TILE_SIZE * 3 - MARGIN, TILE_SIZE * 3 - MARGIN);
                params.leftMargin = topLeftTile.getCoordX();
                params.topMargin = topLeftTile.getCoordY();
                RobotView robotView = new RobotView(getContext(), COGTile.getCoordX(), COGTile.getCoordY(), COGTile.getIdxX(), COGTile.getIdxY(), dirInt);
                addView(robotView, params);
                mRobot = robotView;
            }
        }

        EditText tbDirection = (EditText) mainActivity.findViewById(R.id.tb_direction);

        // update robot direction if changes
        char dir = getDirFromInt(dirInt);
        Log.e("ArenaView", "robotDActual: " + mRobot.getDir());
        if (!mRobot.getDir().equals(dir)) {
            mRobot.setRotation(dirInt);
            mRobot.setDirInt(dirInt);
            Log.e("MainActivity", "new robot direction: " + dir);

            if (tbDirection.getText().toString() != null && !tbDirection.getText().toString().equals(dir + "")) {
                //tbDirection.setText(dir + "");
                this.mainActivity.setDirText(dir + "");
            }
        }
    }

    private char getDirFromInt(int dirInt) {
        char dir = 'X';

        switch (dirInt) {
            case 0:
                dir = 'N';
                break;
            case 270:
                dir = 'W';
                break;
            case 180:
                dir = 'S';
                break;
            case 90:
                dir = 'E';
                break;
        }

        return dir;
    }

    private int getIntFromDir(char dir) {
        int dirInt = -1;

        switch (dir) {
            case 'N':
                dirInt = 0;
                break;
            case 'W':
                dirInt = 270;
                break;
            case 'S':
                dirInt = 180;
                break;
            case 'E':
                dirInt = 90;
                break;
        }

        return dirInt;
    }

    public int getIntFromDirStr(String dirStr) {
        int dirInt = -1;

        switch (dirStr) {
            case "up":
                dirInt = 0;
                break;
            case "left":
                dirInt = 270;
                break;
            case "down":
                dirInt = 180;
                break;
            case "right":
                dirInt = 90;
                break;
        }

        return dirInt;
    }

    public ObstacleView addObstacle(ArenaTileView tileView, char dirChar) {
        ObstacleView obstacle = addObstacle(tileView);
        obstacle.setImageDir(dirChar);

        return obstacle;
    }

    public ObstacleView addObstacle(ArenaTileView tileView) {
        if (tileView == null || tileView.getObstacle() != null) {
            return null;
        }

        Log.e("MainActivity", "adding obstacle ...");

        // increase the size of the obstacle to fit across multiple tiles according to OBSTACLE_SIZE
        // RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(TILE_SIZE - MARGIN, TILE_SIZE - MARGIN);
        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(TILE_SIZE * OBSTACLE_SIZE - MARGIN, TILE_SIZE * OBSTACLE_SIZE - MARGIN);

        /*params.leftMargin = tileView.getCoordX();
        params.topMargin = tileView.getCoordY();*/
        ArenaTileView topLeftTileView = getTopLeftTileFromCOGTileForObs(tileView);
        ArenaTileView COGTileView = tileView;

        if (topLeftTileView == null) return null;

        params.leftMargin = topLeftTileView.getCoordX();
        params.topMargin = topLeftTileView.getCoordY();
        /*params.leftMargin = tileView.getCoordX();
        params.topMargin = tileView.getCoordY();*/

        ObstacleView obstacleView = new ObstacleView(getContext(), obstacleSpawned, COGTileView.getCoordX(), COGTileView.getCoordY(), COGTileView.getIdxX(), COGTileView.getIdxY(), TILE_SIZE * OBSTACLE_SIZE);
        // ObstacleView obstacleView = new ObstacleView(getContext(), obstacleSpawned, tileView.getCoordX(), tileView.getCoordY(), tileView.getIdxX(), tileView.getIdxY(), TILE_SIZE * OBSTACLE_SIZE);

        obstacleView.mainActivity = this.mainActivity;
        addView(obstacleView, params);
        tileView.setObstacle(obstacleView);
        obstacleSpawned++;

        storeObstacles();

        return obstacleView;
    }

    protected void removeObstacle(ArenaTileView tileView) {
        if (tileView == null || tileView.getObstacle() == null) {
            return;
        }

        Log.e("MainActivity", "removing obstacle ...");

        ObstacleView targetObstacle = tileView.getObstacle();

        this.mainActivity.sendMessageToAMD("{ \"removeObstacle:\" [" + targetObstacle.getIdxX() + ", " + targetObstacle.getIdxY() + ", " + targetObstacle.getId() + "] }");
        // this.mainActivity.sendMessageToAMD("{ \"removeObstacle:\" [" + targetObstacle.getId() + "] }");

        removeView(targetObstacle);
        tileView.setObstacle(null);

        storeObstacles();
    }

    public void removeObstacle(int obstacleId) {
        // find the Tile with obstacle matches the target ID
        removeObstacle(getTileFromObstacleId(obstacleId));
    }

    protected void moveObstacle(ArenaTileView fromTile, ArenaTileView toTile) {
        if (fromTile == null || toTile == null) {
            return;
        }

        if (fromTile.getObstacle() == null || toTile.getObstacle() != null) {
            return;
        }

        if (fromTile == toTile) return;

        ObstacleView obstacleView = fromTile.getObstacle();
        RelativeLayout.LayoutParams layoutParams = (RelativeLayout.LayoutParams) obstacleView.getLayoutParams();

        ArenaTileView COGTile = toTile;
        ArenaTileView topLeftTile = getTopLeftTileFromCOGTileForObs(toTile);

        if (topLeftTile == null) return;

        layoutParams.leftMargin = topLeftTile.getCoordX();
        layoutParams.topMargin = topLeftTile.getCoordY();

        obstacleView.setLayoutParams(layoutParams);

        COGTile.setObstacle(obstacleView);

        obstacleView.setCoordX(COGTile.getCoordX());
        obstacleView.setCoordY(COGTile.getCoordY());
        obstacleView.setIdxX(COGTile.getIdxX());
        obstacleView.setIdxY(COGTile.getIdxY());
        obstacleView.onAxisChanged();

        fromTile.setObstacle(null);

        storeObstacles();
    }

    protected ArenaTileView getTileFromObstacleId(int obstacleId) {
        for (int m = 0; m < ROWS; m++) {
            for (int n = 0; n < COLS; n++) {
                ArenaTileView tileView = this.mTiles[m][n];
                if (tileView != null && tileView.getObstacle() != null && tileView.getObstacle().getId() == obstacleId) {
                    return tileView;
                }
            }
        }

        return null;
    }

    public ArenaTileView getTileFromIdx(int x, int y) {
        if (x < 0 || x >= COLS) return null;
        if (y < 0 || y >= ROWS) return null;

        return this.mTiles[y][x];
    }

    public ArenaTileView getTileFromAxis(int x, int y) {
        Point idxPoint = getIdxFromAxis(new Point(x, y));

        if (idxPoint.x < 0 || idxPoint.x >= COLS) return null;
        if (idxPoint.y < 0 || idxPoint.y >= ROWS) return null;

        return this.mTiles[idxPoint.y][idxPoint.x];
    }

    /** convert axis to idx on arena map */
    public Point getIdxFromAxis(Point axis) {
        // return new Point(axis.x - 1, ROWS - axis.y);
        return new Point(axis.x, ROWS - (axis.y + 1));
    }

    public Point getAxisFromIdx(Point idxPoint) {
        return new Point(idxPoint.x, ArenaView.ROWS - idxPoint.y - 1);
    }

    private ArenaTileView getTopLeftTileFromCOGTile(ArenaTileView COGTile) {
        Point cogTileAxis = COGTile.getAxisFromIdx();
        Point topLeftTileAxis = new Point(cogTileAxis.x - COG.x, cogTileAxis.y - COG.y);
        Point topLeftIdx = getIdxFromAxis(topLeftTileAxis);

        return getTileFromIdx(topLeftIdx.x, topLeftIdx.y);
    }

    private ArenaTileView getTopLeftTileFromCOGTileForObs(ArenaTileView COGTile) {
        // just return the COGTile as top left tile for EX algo obstacle, since obs is 1x1
//        if (getCurrentAlgoType().equals(MainActivity.AlgoType.EX.name())) {
//            return COGTile;
//        }

        // ensure COG offset from top left tile not greater than TILE_SIZE
        int COGx = COG.x;
        int COGy = COG.y;
        if (Math.abs(COGx) >= OBSTACLE_SIZE) {
            if (COGx < 0)
                COGx += 1;
            else
                COGx -= 1;
        }

        if (Math.abs(COGy) >= OBSTACLE_SIZE) {
            if (COGy < 0)
                COGy += 1;
            else
                COGy -= 1;
        }

        Point cogTileAxis = COGTile.getAxisFromIdx();
        Point topLeftTileAxis = new Point(cogTileAxis.x - COGx, cogTileAxis.y - COGy);
        Point topLeftIdx = getIdxFromAxis(topLeftTileAxis);

        return getTileFromIdx(topLeftIdx.x, topLeftIdx.y);
    }

    /** returns a list of obstacles currently on arena */
    public List<ObstacleView> getObstacles() {
        List<ObstacleView> listObstacle = new ArrayList<>();

        for (int m = 0; m < ROWS; m++) {
            for (int n = 0; n < COLS; n++) {
                ArenaTileView tile = this.mTiles[m][n];
                if (tile.getObstacle() != null)
                    listObstacle.add(tile.getObstacle());
            }
        }

        return listObstacle;
    }

    /** create a tile group on map based on the provided top left axis */
    public void createTileGroup(Point topLeftAxis) {
        Point topLeftIdx = getIdxFromAxis(topLeftAxis);
        List<ArenaTileView> tileList = new ArrayList<>();
        boolean isGroup = true;

        // set all tiles as highlighted based on the OBSTACLE_SIZE, starting from top left tile
        for (int m = 0; m < OBSTACLE_SIZE; m++) {
            for (int n = 0; n < OBSTACLE_SIZE; n++) {
                ArenaTileView tile = getTileFromIdx(topLeftIdx.x + n, topLeftIdx.y + m);
                if (tile == null) {
                    // if any tile is null, the whole group cannot be formed, just exit the function
                    isGroup = false;
                    break;
                } else {
                    tileList.add(tile);
                }
            }

            if (!isGroup) break;
        }

        if (isGroup) {
            // highlight all tiles in the list
            for (ArenaTileView tile : tileList) {
                tile.setHighlighted(true);
            }
        }
    }

    /** reset the arena, clearing all obstacles */
    public void reset() {
        for (int m = 0; m < ROWS; m++) {
            for (int n = 0; n < ROWS; n++) {
                ArenaTileView tile = mTiles[m][n];
                if (tile.getObstacle() != null)
                    removeObstacle(tile);
            }
        }

        Point idxPoint = getIdxFromAxis(new Point(1, 1));
        updateRobotPosition(idxPoint.x, idxPoint.y, 0);
    }

    public void calculatePathFromInternal() {
        List<ObstacleView> listObstacle = getObstacles();
        Algo algo = Algo.setObstacleSize(listObstacle.size());

        Log.e("algo", "algo set obstacle size: " + listObstacle.size());

        for (ObstacleView obstacle : listObstacle) {
            Log.e("algo", "adding obstacle to algo: " + obstacle);
            algo.addObstacle(obstacle.getAxisFromIdx(), obstacle.getImageDirStr(), obstacle.getId());
        }

        JSONObject pathObj;
        try {
            pathObj = algo.buildPath();
            Log.e("algo", "path: " + pathObj);
            this.calculatedPath = pathObj.getJSONArray("path");
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }

        if (this.arenaPathLineView != null) {
            this.arenaPathLineView.drawPathLines();
        }

        // send path to robot
        this.mainActivity.sendMessageToAMD(pathObj.toString().replaceAll("\n", "").replaceAll("\r", ""));
        Toast.makeText(mainActivity, "Using algoSQ, local calculated path sent to robot", Toast.LENGTH_SHORT).show();

        Log.e("algo", "path size: " + this.calculatedPath.length());

        startTimer();

        // move robot for simulation
        if (this.mainActivity.cbSimulation.isChecked())
            moveRobotFromPath(this.calculatedPath, 100);

        this.mainActivity.mStart.setEnabled(true);
    }

    public void calculatePathFromApi() {
        List<ObstacleView> listObstacle = getObstacles();
        JSONObject obsReqObj = new JSONObject();

        // reset robot location
        Point idxPoint = getIdxFromAxis(ROBOT_START_AXIS);
        updateRobotPosition(idxPoint.x, idxPoint.y, 0);

        JSONArray obsArr = new JSONArray();
        for (ObstacleView obstacle : listObstacle) {
            JSONObject obsObj = new JSONObject();

            Point axisPoint = obstacle.getAxisFromIdx();
            try {
                obsObj.put("x", axisPoint.x);
                obsObj.put("y", axisPoint.y);
                obsObj.put("id", obstacle.getId());
                obsObj.put("d", getDirIntFromStrForApi(obstacle.getImageDirStr()));
                obsArr.put(obsObj);
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
        }

        try {
            obsReqObj.put("obstacles", obsArr);
            if (this.mRobot != null) {
                Point axisPoint = mRobot.getAxisFromIdx();
                obsReqObj.put("robot_x", axisPoint.x);
                obsReqObj.put("robot_y", axisPoint.y);
                obsReqObj.put("robot_dir", mRobot.getDirIntForApi());
            }
            obsReqObj.put("retrying", false);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }

        Toast.makeText(mainActivity, "Using algoEX, sending arena info to server ...", Toast.LENGTH_SHORT).show();
        this.apiTask = (ApiTask) new ApiTask(this).execute(obsReqObj);
    }

    private final List<ArenaTileView> listPathTile = new ArrayList<>();
    /** draw the calculated path lines from result, for manual movement */
    public void drawPathLines() {
        if (this.calculatedPath != null) {
            listPathTile.clear();

            for (int i = 0; i < this.calculatedPath.length(); i++) {
//                try {
//                    JSONObject pathObj = this.calculatedPath.getJSONObject(i);
//                    ArenaTileView tile = getTileFromAxis(pathObj.getInt("x"), pathObj.getInt("y"));
//                    if (tile != null) {
//                        tile.setHighlighted(true);
//                    }
//                } catch (JSONException e) {
//                    throw new RuntimeException(e);
//                }
                JSONObject pathObj = null;
                try {
                    pathObj = this.calculatedPath.getJSONObject(i);
                    ArenaTileView tile = getTileFromAxis(pathObj.getInt("x"), pathObj.getInt("y"));
                    listPathTile.add(tile);
                } catch (JSONException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    public void onApiResult(JSONObject result) {
        // convert field "d" value from dirStr to dirInt
        try {
            this.calculatedPath = result.getJSONArray("path");
            this.calculatedCommands = result.getJSONArray("commands");
            for (int i = 0; i < this.calculatedPath.length(); i++) {
                JSONObject pathObj = this.calculatedPath.getJSONObject(i);
                pathObj.put("d", getDirStrFromIntForApi(pathObj.getInt("d")));
            }
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }

        if (this.arenaPathLineView != null) {
            this.arenaPathLineView.drawPathLines();
        }

        this.mainActivity.sendMessageToAMD(result.toString().replace("\n", "").replace("\r", ""));
        Toast.makeText(mainActivity, "Sent calculated path from server to robot", Toast.LENGTH_SHORT).show();

        startTimer();

        if (this.mainActivity.cbSimulation.isChecked())
            moveRobotFromPath(this.calculatedPath, 1000);

        this.mainActivity.mStart.setEnabled(true);
    }

    private void startTimer() {
        this.mainActivity.mStart.setText("STOP");
        TextView tvTimer = (TextView) ArenaView.this.mainActivity.findViewById(R.id.tv_timer);

//        this.timerRunnable = new Runnable() {
//            private long timerMs = 0;
//
//            @Override
//            public void run() {
//                TextView tvTimer = (TextView) ArenaView.this.mainActivity.findViewById(R.id.tv_timer);
//
//                long sec = timerMs / 1000;
//                long ms = timerMs % 1000;
//                long min = sec / 60;
//
//                tvTimer.setText(min + ":" + sec + ":" + ms);
//
//                timerMs += 100;
//
//                if (timerHandler != null)
//                    timerHandler.postDelayed(ArenaView.this.timerRunnable, 100);
//            }
//        };
//
//        this.timerHandler = new Handler();
//        timerHandler.postDelayed(this.timerRunnable, 100);

        timer = new CountUpTimer(10 * 60 * 1000) {
            public void onTick(int second) {
                tvTimer.setText(String.valueOf(second) + "s");
            }
        };

        timer.start();
    }

    public void stopTimer() {
        this.moveRobotHandler = null;
        this.timerHandler = null;
        if (this.timer != null) {
            this.timer.cancel();
            this.timer = null;
        }
        this.mainActivity.mStart.setText("START");
    }

    private void moveRobotFromPath(JSONArray pathObjArr, int delayPerStep) {
        moveRobotRunnable = new Runnable() {
            private int idx = 0;

            @Override
            public void run() {
                JSONObject obj;
                try {
                    obj = pathObjArr.getJSONObject(idx);
                } catch (JSONException e) {
                    throw new RuntimeException(e);
                }

                Log.e("algo", "robot status: " + obj);

                // move robot
                Point idxPoint;
                try {
                    idxPoint = getIdxFromAxis(new Point(obj.getInt("x"), obj.getInt("y")));
                    Log.e("ArenaView", "robotD: " + getIntFromDirStr(obj.getString("d")));
                    updateRobotPosition(idxPoint.x, idxPoint.y, getIntFromDirStr(obj.getString("d")));
                } catch (JSONException e) {
                    throw new RuntimeException(e);
                }

                if (idx < pathObjArr.length() - 1) {
                    if (moveRobotHandler != null)
                        moveRobotHandler.postDelayed(this, delayPerStep);
                    idx++;
                } else {
                    Toast.makeText(mainActivity, "Simulation path completed, stopped timer", Toast.LENGTH_LONG).show();
                    // stop the timer
                    stopTimer();
                }
            }
        };

        moveRobotHandler = new Handler();
        moveRobotHandler.postDelayed(moveRobotRunnable, delayPerStep);
    }

    private int getDirIntFromStrForApi(String dirStr) {
        switch (dirStr) {
            case "up":
                return 0;
            case "right":
                return 2;
            case "down":
                return 4;
            case "left":
                return 6;
            default:
                return 8;
        }
    }

    private int getDirIntFromStrForRobot(String dirStr) {
        switch (dirStr) {
            case "up":
                return 0;
            case "right":
                return 90;
            case "down":
                return 180;
            case "left":
                return 270;
            default:
                return -1;
        }
    }

    private int getDirIntFromIntForApi(String dirStr) {
        switch (dirStr) {
            case "up":
                return 0;
            case "right":
                return 2;
            case "down":
                return 4;
            case "left":
                return 6;
            default:
                return 8;
        }
    }

    private String getDirStrFromIntForApi(int dirInt) {
        switch (dirInt) {
            case 0:
                return "up";
            case 2:
                return "right";
            case 4:
                return "down";
            case 6:
                return "left";
            default:
                return "none";
        }
    }

    private String getCurrentAlgoType() {
        SharedPreferences sh = this.mainActivity.getSharedPreferences("MySharedPref", MODE_PRIVATE);
        return sh.getString("algo_type", MainActivity.AlgoType.EX.name());
    }

    public boolean isAllObsSideSet() {
        List<ObstacleView> listObstacle = getObstacles();
        for (int i = 0; i < listObstacle.size(); i++) {
            ObstacleView obs = listObstacle.get(i);
            if (obs != null && obs.getImageDir() == 'x') {
                return false;
            }
        }

        return true;
    }

    /** store obstacles in shared pref */
    public void storeObstacles() {
        Set<String> obsSet = new HashSet<>();

        for (ObstacleView obs : getObstacles()) {
            // id,idxX,idxY,dirChar,imageId
            String data = obs.getId() + "," + obs.getIdxX() + "," + obs.getIdxY() + "," + obs.getImageDir() + "," + obs.getImageTargetId();
            obsSet.add(data);
        }

        SharedPreferences sh = this.mainActivity.getSharedPreferences("MySharedPref", MODE_PRIVATE);
        SharedPreferences.Editor myEdit = sh.edit();

        myEdit.putStringSet("obstacles", obsSet);

        myEdit.apply();
    }

    public Set<String> getStoredObstacles() {
        SharedPreferences sh = this.mainActivity.getSharedPreferences("MySharedPref", MODE_PRIVATE);

        return sh.getStringSet("obstacles", new HashSet<>());
    }

    public void moveRobotForward() {
        if (this.mRobot == null) return;

        Point axisPoint = mRobot.getAxisFromIdx();

        switch (mRobot.getDirInt()) {
            case 0:
                // when facing up
                axisPoint.y += this.mainActivity.getRobotStraightDistancePerMove();
                break;
            case 90:
                // when facing right
                axisPoint.x += this.mainActivity.getRobotStraightDistancePerMove();
                break;
            case 180:
                // when facing down
                axisPoint.y -= this.mainActivity.getRobotStraightDistancePerMove();
                break;
            case 270:
                // when facing left
                axisPoint.x -= this.mainActivity.getRobotStraightDistancePerMove();
                break;
        }

        Point idxPoint = getIdxFromAxis(axisPoint);
        updateRobotPosition(idxPoint.x, idxPoint.y, mRobot.getDirInt());
    }

    public void moveRobotBackward() {
        if (this.mRobot == null) return;

        Point axisPoint = mRobot.getAxisFromIdx();

        switch (mRobot.getDirInt()) {
            case 0:
                // when facing up
                axisPoint.y -= this.mainActivity.getRobotStraightDistancePerMove();
                break;
            case 90:
                // when facing right
                axisPoint.x -= this.mainActivity.getRobotStraightDistancePerMove();
                break;
            case 180:
                // when facing down
                axisPoint.y += this.mainActivity.getRobotStraightDistancePerMove();
                break;
            case 270:
                // when facing left
                axisPoint.x += this.mainActivity.getRobotStraightDistancePerMove();
                break;
        }

        Point idxPoint = getIdxFromAxis(axisPoint);
        updateRobotPosition(idxPoint.x, idxPoint.y, mRobot.getDirInt());
    }

    public void moveRobotLeft() {
        if (this.mRobot == null) return;

        String distance = this.mainActivity.getRobotTurningDistancePerMove();
        int xOffset = Integer.parseInt(distance.split(",")[0]);
        int yOffset = Integer.parseInt(distance.split(",")[1]);
        Point axisPoint = mRobot.getAxisFromIdx();

        Point idxPoint;
        switch (mRobot.getDirInt()) {
            // when facing up
            case 0:
                axisPoint.x -= xOffset;
                axisPoint.y += yOffset;
                idxPoint = getIdxFromAxis(axisPoint);
                updateRobotPosition(idxPoint.x, idxPoint.y, 270);
                break;
            // when facing right
            case 90:
                axisPoint.y += xOffset;
                axisPoint.x += yOffset;
                idxPoint = getIdxFromAxis(axisPoint);
                updateRobotPosition(idxPoint.x, idxPoint.y, 0);
                break;
                // when facing down
            case 180:
                axisPoint.x += xOffset;
                axisPoint.y -= yOffset;
                idxPoint = getIdxFromAxis(axisPoint);
                updateRobotPosition(idxPoint.x, idxPoint.y, 90);
                break;
            // when facing left
            case 270:
                axisPoint.y -= xOffset;
                axisPoint.x -= yOffset;
                idxPoint = getIdxFromAxis(axisPoint);
                updateRobotPosition(idxPoint.x, idxPoint.y, 180);
                break;
        }
    }

    public void moveRobotRight() {
        if (this.mRobot == null) return;

        String distance = this.mainActivity.getRobotTurningDistancePerMove();
        int xOffset = Integer.parseInt(distance.split(",")[0]);
        int yOffset = Integer.parseInt(distance.split(",")[1]);
        Point axisPoint = mRobot.getAxisFromIdx();

        Point idxPoint;
        switch (mRobot.getDirInt()) {
            // when facing up
            case 0:
                axisPoint.x += xOffset;
                axisPoint.y += yOffset;
                idxPoint = getIdxFromAxis(axisPoint);
                updateRobotPosition(idxPoint.x, idxPoint.y, 90);
                break;
            // when facing right
            case 90:
                axisPoint.y -= xOffset;
                axisPoint.x += yOffset;
                idxPoint = getIdxFromAxis(axisPoint);
                updateRobotPosition(idxPoint.x, idxPoint.y, 180);
                break;
            // when facing down
            case 180:
                axisPoint.x -= xOffset;
                axisPoint.y -= yOffset;
                idxPoint = getIdxFromAxis(axisPoint);
                updateRobotPosition(idxPoint.x, idxPoint.y, 270);
                break;
            // when facing left
            case 270:
                axisPoint.y += xOffset;
                axisPoint.x -= yOffset;
                idxPoint = getIdxFromAxis(axisPoint);
                updateRobotPosition(idxPoint.x, idxPoint.y, 0);
                break;
        }
    }
}
