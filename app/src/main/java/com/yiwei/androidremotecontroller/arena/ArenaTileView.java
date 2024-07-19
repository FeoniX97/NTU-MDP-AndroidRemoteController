package com.yiwei.androidremotecontroller.arena;

import android.content.ClipData;
import android.content.ClipDescription;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.util.Log;
import android.view.DragEvent;
import android.view.View;
import android.widget.Toast;

import com.yiwei.androidremotecontroller.MainActivity;

public class ArenaTileView extends View {
    private final int coordX;
    private final int coordY;
    private final int idxX;
    private final int idxY;
    private final int size;
    private final ArenaView arenaView;
    private final boolean isCOG; // whether this tile is the center of gravity for the 3x3 tile group

    private final Paint mBluePaintBrushFill;
    private final Paint mOrangePaintBrushFill;
    private final Rect mRect;

    private ObstacleView mObstacle;
    private boolean mHighlighted = false;

    public ArenaTileView(Context context, int coordX, int coordY, int idxX, int idxY, int size, boolean isCOG, ArenaView arenaView) {
        super(context);

        this.coordX = coordX;
        this.coordY = coordY;
        this.idxX = idxX;
        this.idxY = idxY;
        this.size = size;
        this.isCOG = isCOG;
        this.arenaView = arenaView;

        mBluePaintBrushFill = new Paint();
        mBluePaintBrushFill.setColor(Color.rgb(169, 229, 253));
        mBluePaintBrushFill.setStyle(Paint.Style.FILL);

        mOrangePaintBrushFill = new Paint();
        mOrangePaintBrushFill.setColor(Color.rgb(255, 152, 0));
        mOrangePaintBrushFill.setStyle(Paint.Style.FILL);

        mRect = new Rect();
        mRect.set(0, 0, size, size);

        setupDragListener();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        canvas.drawRect(mRect, this.mHighlighted ? mOrangePaintBrushFill : mBluePaintBrushFill);
    }

    private void setupDragListener() {
        // Set the drag event listener for the Tile.
        setOnDragListener( (v, e) -> {

            // Handle each of the expected events.
            switch(e.getAction()) {

                case DragEvent.ACTION_DRAG_STARTED:

                    // return false if the Tile already has obstacle and cannot accept drop
                    return this.getObstacle() == null;

                case DragEvent.ACTION_DRAG_ENTERED:

                    // highlight the Tile
                    this.setHighlighted(true);

                    // Return true. The value is ignored.
                    return true;

                case DragEvent.ACTION_DRAG_LOCATION:

                    // Ignore the event.
                    return true;

                case DragEvent.ACTION_DRAG_EXITED:

                    // remove highlight of the Tile
                    this.setHighlighted(false);

                    // Return true. The value is ignored.
                    return true;

                case DragEvent.ACTION_DROP:

                    // Get the item containing the dragged data.
                    ClipData.Item item = e.getClipData().getItemAt(0);

                    // Get the obstacle id from the item.
                    CharSequence obstacleIdStr = item.getText();
                    int obstacleId = Integer.parseInt(obstacleIdStr + "");

                    // Display a message containing the dragged data.
                    Log.e("MainActivity", this.getIdxX() + ", " + this.getIdxY() + ": DragEvent.ACTION_DROP: " + obstacleId);

                    if (obstacleId > -1) {
                        // move the obstacle in arena
                        ArenaTileView fromTile = this.arenaView.getTileFromObstacleId(obstacleId);
                        Point from = new Point(fromTile.getIdxX(), fromTile.getIdxY());
                        Point to = new Point(this.getIdxX(), this.getIdxY());
                        this.arenaView.moveObstacle(fromTile, this);
                        this.arenaView.mainActivity.sendMessageToAMD("{ \"moveObstacle:\" [" + from.x + ", " + from.y + ", " + to.x + ", " + to.y + ", " + obstacleId + "] }");
                    } else {
                        // inform arena to create new obstacle from legend
                        ObstacleView newObstacle = this.arenaView.addObstacle(this);
                        newObstacle.mainActivity = this.arenaView.mainActivity;
                        this.arenaView.mainActivity.sendMessageToAMD("{ \"addObstacle:\" [" + newObstacle.getIdxX() + ", " + newObstacle.getIdxY() + ", " + newObstacle.getId() + "] }");
                    }

                    // inform arena to create new obstacle from legend
                    // get the tile group top left axis, based on this current tile as the COG
                    /*Point currentAxis = getAxisFromIdx();
                    Point topLeftAxis = new Point(currentAxis.x - ArenaView.COG.x, currentAxis.y - ArenaView.COG.y);

                    // inform arena to create tile group highlighting
                    this.arenaView.createTileGroup(topLeftAxis);*/

                    // Return true. DragEvent.getResult() returns true.
                    return true;

                case DragEvent.ACTION_DRAG_ENDED:

                    // remove highlight
                    this.setHighlighted(false);

                    // Do a getResult() and displays what happens.
//                    if (e.getResult()) {
//                        Log.e("MainActivity", "The drop was handled.");
//                    } else {
//                        Log.e("MainActivity", "The drop didn't work.");
//                    }

                    // Return true. The value is ignored.
                    return true;

                // An unknown action type is received.
                default:
                    Log.e("DragDrop Example","Unknown action type received by View.OnDragListener.");
                    break;
            }

            return false;

        });
    }

    public ObstacleView getObstacle() {
        return mObstacle;
    }

    public void setObstacle(ObstacleView mObstacle) {
        this.mObstacle = mObstacle;
    }

    public boolean isHighlighted() {
        return mHighlighted;
    }

    public void setHighlighted(boolean mHighlighted) {
        this.mHighlighted = mHighlighted;

        // redraw the tile
        this.invalidate();
    }

    public int getCoordX() {
        return coordX;
    }

    public int getCoordY() {
        return coordY;
    }

    public int getIdxX() {
        return idxX;
    }

    public int getIdxY() {
        return idxY;
    }

    public int getSize() {
        return size;
    }

    /** convert idx to axis on arena map */
    public Point getAxisFromIdx() {
        return new Point(this.idxX + 1, ArenaView.ROWS - this.idxY);
    }
}
