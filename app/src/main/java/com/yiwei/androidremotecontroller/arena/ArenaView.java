package com.yiwei.androidremotecontroller.arena;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.util.Log;
import android.widget.EditText;
import android.widget.RelativeLayout;

import com.yiwei.androidremotecontroller.MainActivity;
import com.yiwei.androidremotecontroller.R;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class ArenaView extends RelativeLayout {

    // constants
    private final int ROWS = 15;
    private final int COLS = 14;
    private final int LEFT_OFFSET = 30;
    private final int MARGIN = 5;
    private final int TILE_SIZE = 50;

    // paint brushes
    private final Paint mBlackPaintBrushFill;
    private final Paint mBluePaintBrushFill;

    // background drawings
    private final Rect[][] mRects = new Rect[ROWS][COLS];
    private final Point[] mCoordsY = new Point[ROWS];
    private final Point[] mCoordsX = new Point[COLS];

    // tiles and robot, obstacle is stored in individual tile object
    private final ArenaTileView[][] mTiles = new ArenaTileView[ROWS][COLS];
    private RobotView mRobot;
    private int obstacleSpawned = 0;

    public ArenaView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);

        setWillNotDraw(false);

        mBlackPaintBrushFill = new Paint();
        mBlackPaintBrushFill.setColor(Color.BLACK);
        mBlackPaintBrushFill.setStyle(Paint.Style.FILL);
        mBlackPaintBrushFill.setTextSize(16f);
        mBlackPaintBrushFill.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));

        mBluePaintBrushFill = new Paint();
        mBluePaintBrushFill.setColor(Color.GRAY);
        mBluePaintBrushFill.setStyle(Paint.Style.FILL);

        for (int m = 0; m < ROWS; m++) {
            for (int n = 0; n < COLS; n++) {
                int left = n * TILE_SIZE + MARGIN + LEFT_OFFSET;
                int top = m * TILE_SIZE + MARGIN;
                int right = n * TILE_SIZE + TILE_SIZE + LEFT_OFFSET;
                int bottom = m * TILE_SIZE + TILE_SIZE;

                mRects[m][n] = new Rect();
                mRects[m][n].set(left, top, right, bottom);

                // add tile view to rect
                // stretch some offset height for most bottom rows
                int offset = (m == ROWS - 1) ? 4 : 0;
                RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(TILE_SIZE - MARGIN, TILE_SIZE - MARGIN + offset);
                params.leftMargin = left;
                params.topMargin = top;
                ArenaTileView tileView = new ArenaTileView(getContext(), left, top, n, m, TILE_SIZE, this);
                addView(tileView, params);

                // save tile to array
                mTiles[m][n] = tileView;

                // save coordinates X
                if (m == 0)
                    mCoordsX[n] = new Point(n * TILE_SIZE + TILE_SIZE, TILE_SIZE * ROWS + MARGIN + 20);
            }

            // save coordinates Y
            mCoordsY[m] = new Point(MARGIN, m * TILE_SIZE + (MARGIN + TILE_SIZE / 2 + 2));
        }
    }

    /** Draw the Arena background */
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        for (int m = 0; m < ROWS; m++) {
            for (int n = 0; n < COLS; n++) {
                canvas.drawRect(mRects[m][n], mBluePaintBrushFill);

                if (m == 0) {
                    // draw coordinates X
                    Point point = mCoordsX[n];
//                    if (n < 10) {
//                        // add some offset to digit if x < 10
//                        point.x += 2;
//                    }
                    canvas.drawText(String.valueOf(n), point.x, point.y, mBlackPaintBrushFill);
                }
            }

            // draw coordinates Y
            Point point = mCoordsY[m];
            canvas.drawText(String.valueOf(m), point.x, point.y, mBlackPaintBrushFill);
        }
    }

    /** On receive message from robot, passed from MainActivity */
    public void onMessage(JSONObject msgObj, MainActivity mainActivity) {
        JSONArray robotPosition = null;
        try {
            robotPosition = msgObj.getJSONArray("robotPosition");
            int x = robotPosition.getInt(0);
            int y = robotPosition.getInt(1);
            int dirInt = robotPosition.getInt(2);
            String dir = "";

            switch (dirInt) {
                case 0:
                    dir = "N";
                    break;
                case 270:
                    dir = "W";
                    break;
                case 180:
                    dir = "S";
                    break;
                case 90:
                    dir = "E";
                    break;
            }

            // spawn robot or change robot location if coordinates changes
            if (mRobot == null || mRobot.getIdxX() != x || mRobot.getIdxY() != y) {
                Log.e("MainActivity", "new robot coord: " + x + ", " + y);

                // display the new robot coordinates in textbox
                ((EditText) mainActivity.findViewById(R.id.tb_coord)).setText(x + ", " + y);

                // spawn and display the robot on arena, delete the old one if already exists
                if (mRobot != null) {
                    removeView(mRobot);
                    mRobot = null;
                }

                ArenaTileView tileView = mTiles[y][x];
                if (tileView != null) {
                    RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(TILE_SIZE - MARGIN, TILE_SIZE - MARGIN);
                    params.leftMargin = tileView.getCoordX();
                    params.topMargin = tileView.getCoordY();
                    RobotView robotView = new RobotView(getContext(), tileView.getCoordX(), tileView.getCoordY(), tileView.getIdxX(), tileView.getIdxY(), dirInt);
                    addView(robotView, params);
                    mRobot = robotView;
                }
            }

            // update robot direction if changes
            if (!mRobot.getDir().equals(dir)) {
                mRobot.setRotation(dirInt);
                mRobot.setDirInt(dirInt);
                Log.e("MainActivity", "new robot direction: " + dir);
            }

            ((EditText) mainActivity.findViewById(R.id.tb_direction)).setText(dir);
        } catch (JSONException ignored) {}

        JSONArray obstacle = null;
        try {
            obstacle = msgObj.getJSONArray("obstacle");
            int x = obstacle.getInt(0);
            int y = obstacle.getInt(1);
            int flag = obstacle.getInt(2); // added: 1, removed: 0

            Log.e("MainActivity", "obstacle: " + x + ", " + y + ", " + flag);

            ArenaTileView tileView = mTiles[y][x];
            if (flag == 1 && tileView != null && tileView.getObstacle() == null) {
                // when flag == 1, spawn obstacle if not already exists
                addObstacle(tileView);
            } else if (flag == 0 && tileView.getObstacle() != null) {
                // when flag == 0, remove obstacle if exists
                removeObstacle(tileView);
            }
        } catch (Exception e) {
            Log.e("MainActivity", e.getMessage());
        }
    }

    protected void addObstacle(ArenaTileView tileView) {
        if (tileView == null || tileView.getObstacle() != null) {
            return;
        }

        Log.e("MainActivity", "adding obstacle ...");

        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(TILE_SIZE - MARGIN, TILE_SIZE - MARGIN);
        params.leftMargin = tileView.getCoordX();
        params.topMargin = tileView.getCoordY();
        ObstacleView obstacleView = new ObstacleView(getContext(), obstacleSpawned, tileView.getCoordX(), tileView.getCoordY(), tileView.getIdxX(), tileView.getIdxY(), TILE_SIZE);
        addView(obstacleView, params);
        tileView.setObstacle(obstacleView);
        obstacleSpawned++;
    }

    protected void removeObstacle(ArenaTileView tileView) {
        if (tileView == null || tileView.getObstacle() == null) {
            return;
        }

        Log.e("MainActivity", "removing obstacle ...");

        removeView(tileView.getObstacle());
        tileView.setObstacle(null);
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

        ObstacleView obstacleView = fromTile.getObstacle();
        RelativeLayout.LayoutParams layoutParams = (RelativeLayout.LayoutParams) obstacleView.getLayoutParams();

        layoutParams.leftMargin = toTile.getCoordX();
        layoutParams.topMargin = toTile.getCoordY();

        obstacleView.setLayoutParams(layoutParams);

        toTile.setObstacle(obstacleView);
        fromTile.setObstacle(null);
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
}