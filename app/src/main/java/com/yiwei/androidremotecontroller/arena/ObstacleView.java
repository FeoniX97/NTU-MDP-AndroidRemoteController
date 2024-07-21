package com.yiwei.androidremotecontroller.arena;

import static com.yiwei.androidremotecontroller.arena.ArenaView.MARGIN;
import static com.yiwei.androidremotecontroller.arena.ArenaView.OBSTACLE_SIZE;
import static com.yiwei.androidremotecontroller.arena.ArenaView.TILE_SIZE;

import android.content.ClipData;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.support.annotation.Nullable;
import android.support.v7.widget.AppCompatImageView;
import android.support.v7.widget.PopupMenu;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

import com.yiwei.androidremotecontroller.MainActivity;
import com.yiwei.androidremotecontroller.R;

public class ObstacleView extends AppCompatImageView {

    private static final char IMAGE_DIR_NORTH = 'n';
    private static final char IMAGE_DIR_WEST = 'w';
    private static final char IMAGE_DIR_EAST = 'e';
    private static final char IMAGE_DIR_SOUTH = 's';
    private static final char IMAGE_DIR_NONE = 'x';

    private final int id;
    private final int coordX;
    private final int coordY;
    private final int idxX;
    private final int idxY;
    private final int size;

    // whether this obstacle is a legend outside the arena
    private final boolean isLegend;

    private final Paint mBlackPaintBrushFill;
    private final Paint mWhitePaintBrushFill;
    private final Paint mWhitePaintBrushFillBold;
    private final Paint mRedPaintBrushFill;
    private final Rect mRect;
    private final Rect mRectTopBorder;
    private final Rect mRectLeftBorder;
    private final Rect mRectRightBorder;
    private final Rect mRectBottomBorder;
    private final Rect mRectTopBorderThick;
    private final Rect mRectLeftBorderThick;
    private final Rect mRectRightBorderThick;
    private final Rect mRectBottomBorderThick;

    private char mImageDir = IMAGE_DIR_NONE;

    /** the detected imageId, -1 if no image detected */
    private int mImageTargetId = -1;

    public MainActivity mainActivity;

    // constructed from xml, for legend
    public ObstacleView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);

        id = -1;
        coordX = -1;
        coordY = -1;
        idxX = -1;
        idxY = -1;

        TypedArray attrArr = context.obtainStyledAttributes(attrs, R.styleable.ObstacleView);

        isLegend = attrArr.getBoolean(R.styleable.ObstacleView_isLegend, true);
        size = attrArr.getInt(R.styleable.ObstacleView_size, 25);

        attrArr.recycle();

        // legend obstacle does not have index and image direction border
        mWhitePaintBrushFill = null;
        mWhitePaintBrushFillBold = null;
        mRedPaintBrushFill = null;
        mRectTopBorder = mRectLeftBorder = mRectRightBorder = mRectBottomBorder = null;
        mRectTopBorderThick = mRectLeftBorderThick = mRectRightBorderThick = mRectBottomBorderThick = null;

        // for body
        mBlackPaintBrushFill = new Paint();
        mBlackPaintBrushFill.setColor(Color.BLACK);
        mBlackPaintBrushFill.setStyle(Paint.Style.FILL);

        mRect = new Rect();
        mRect.set(0, 0, size, size);

        setupDraggable();
    }

    // constructed from code
    public ObstacleView(Context context, int id, int coordX, int coordY, int idxX, int idxY, int size) {
        super(context);

        this.id = id;
        this.coordX = coordX;
        this.coordY = coordY;
        this.idxX = idxX;
        this.idxY = idxY;
        this.size = size;
        this.isLegend = false;

        // for body
        mBlackPaintBrushFill = new Paint();
        mBlackPaintBrushFill.setColor(Color.BLACK);
        mBlackPaintBrushFill.setStyle(Paint.Style.FILL);

        // for index
        mWhitePaintBrushFill = new Paint();
        mWhitePaintBrushFill.setColor(Color.WHITE);
        mWhitePaintBrushFill.setStyle(Paint.Style.FILL);
        mWhitePaintBrushFill.setTextSize(16f);

        mWhitePaintBrushFillBold = new Paint();
        mWhitePaintBrushFillBold.setColor(Color.WHITE);
        mWhitePaintBrushFillBold.setStyle(Paint.Style.FILL);
        mWhitePaintBrushFillBold.setTextSize(24f);
        mWhitePaintBrushFillBold.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));

        // for image direction border
        mRedPaintBrushFill = new Paint();
        mRedPaintBrushFill.setColor(Color.RED);
        mRedPaintBrushFill.setStyle(Paint.Style.FILL);

        // create and setup body rect
        mRect = new Rect();
        mRect.set(0, 0, size, size);

        // create and setup image direction border rect
        mRectTopBorder = new Rect();
        mRectLeftBorder = new Rect();
        mRectRightBorder = new Rect();
        mRectBottomBorder = new Rect();

        mRectTopBorder.set(0, 0, size, 3);
        mRectLeftBorder.set(0, 0, 3, size);
        mRectRightBorder.set(size - 6, 0, size, size);
        mRectBottomBorder.set(0, size - 6, size, size);

        // create another set of thicker border rect
        mRectTopBorderThick = new Rect();
        mRectLeftBorderThick = new Rect();
        mRectRightBorderThick = new Rect();
        mRectBottomBorderThick = new Rect();

        mRectTopBorderThick.set(0, 0, size, 6);
        mRectLeftBorderThick.set(0, 0, 6, size);
        mRectRightBorderThick.set(size - 9, 0, size, size);
        mRectBottomBorderThick.set(0, size - 9, size, size);

        setupPopupMenu();
        setupDraggable();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawRect(mRect, mBlackPaintBrushFill);

        if (!isLegend()) {
            // draw index
            if (this.getImageTargetId() < 0) {
                // draw normal index, no image detected
                canvas.drawText(String.valueOf(this.id), (float) this.size / 2 - 7 - (this.id > 9 ? 5 : 0), (float) this.size / 2 + 2, this.mWhitePaintBrushFill);
            } else {
                // draw bold targetId
                canvas.drawText(String.valueOf(this.getImageTargetId()), (float) this.size / 2 - 9 - (this.getImageTargetId() > 9 ? 8 : 0), (float) this.size / 2 + 6, this.mWhitePaintBrushFillBold);
            }

            if (this.getImageTargetId() > -1) {
                Log.e("ObstacleView", "drawing detected image");
                super.onDraw(canvas);
            }

            // draw image direction border if exists
            if (getImageDir() != IMAGE_DIR_NONE) {
                switch (getImageDir()) {
                    case IMAGE_DIR_NORTH:
                        canvas.drawRect(this.getImageTargetId() < 0 ? mRectTopBorder : mRectTopBorderThick, mRedPaintBrushFill);
                        break;
                    case IMAGE_DIR_WEST:
                        canvas.drawRect(this.getImageTargetId() < 0 ? mRectLeftBorder : mRectLeftBorderThick, mRedPaintBrushFill);
                        break;
                    case IMAGE_DIR_EAST:
                        canvas.drawRect(this.getImageTargetId() < 0 ? mRectRightBorder : mRectRightBorderThick, mRedPaintBrushFill);
                        break;
                    case IMAGE_DIR_SOUTH:
                        canvas.drawRect(this.getImageTargetId() < 0 ? mRectBottomBorder : mRectBottomBorderThick, mRedPaintBrushFill);
                        break;
                }
            }
        }
    }

    private void setupPopupMenu() {
        setOnClickListener(v -> {
            // Initializing the popup menu and giving the reference as current context
            PopupMenu popupMenu = new PopupMenu(getContext(), v);

            // Inflating popup menu from obstacle_menu.xml file
            popupMenu.getMenuInflater().inflate(R.menu.obstacle_menu, popupMenu.getMenu());

            popupMenu.getMenu().getItem(0).setTitle("Obstacle Id: " + this.getId());
            popupMenu.getMenu().getItem(1).setTitle("Target Id: " + this.getImageTargetId());

            popupMenu.setOnMenuItemClickListener(menuItem -> {
                // Toast message on menu item clicked
                Log.e("Popup Menu",  v.getId() + ": " + menuItem.getTitle());
                try {
                    switch (menuItem.getItemId()) {
                        case R.id.top:
                            setImageDir(IMAGE_DIR_NORTH);
                            break;
                        case R.id.left:
                            setImageDir(IMAGE_DIR_WEST);
                            break;
                        case R.id.right:
                            setImageDir(IMAGE_DIR_EAST);
                            break;
                        case R.id.bottom:
                            setImageDir(IMAGE_DIR_SOUTH);
                            break;
                        case R.id.clear:
                            // clear the image with the targetId
                            setImageDir(IMAGE_DIR_NONE);
                            setImageTargetId(-1);
                            break;
                        case R.id.remove_obstacle:
                            ObstacleView.this.mainActivity.mArenaView.removeObstacle(ObstacleView.this.getId());
                            break;
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }

                return true;
            });

            // Showing the popup menu
            popupMenu.show();
        });
    }

    private void setupDraggable() {
        // Set a long-click listener for the View using an anonymous listener
        // object that implements the OnLongClickListener interface.
        setOnLongClickListener(v -> {
            ClipData dragData = ClipData.newPlainText("index", String.valueOf(((ObstacleView)v).getId()));

            // Instantiate the drag shadow builder. We use this View object
            // to create the default builder.
            View.DragShadowBuilder myShadow = new View.DragShadowBuilder(this);

            // Start the drag.
            v.startDrag(dragData,  // The data to be dragged.
                    myShadow,  // The drag shadow builder.
                    null,      // No need to use local data.
                    0          // Flags. Not currently used, set to 0.
            );

            // Indicate that the long-click is handled.
            return true;
        });
    }

    @Override
    public int getId() {
        return id;
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

    public boolean isLegend() {
        return isLegend;
    }

    public char getImageDir() {
        return mImageDir;
    }

    public String getImageDirStr() {
        switch (this.mImageDir) {
            case 'n':
                return "up";
            case 'e':
                return "right";
            case 'w':
                return "left";
            case 's':
                return "down";
        }

        return "null";
    }

    public void setImageDirFromStr(String dirStr) {
        switch (dirStr) {
            case "up":
                setImageDir('n');
                break;
            case "right":
                setImageDir('e');
                break;
            case "down":
                setImageDir('s');
                break;
            case "left":
                setImageDir('w');
                break;
        }
    }

    public void setImageDir(char mImageDir) {
        mImageDir = Character.toLowerCase(mImageDir);

        if (mImageDir != IMAGE_DIR_NONE && mImageDir != IMAGE_DIR_NORTH && mImageDir != IMAGE_DIR_WEST && mImageDir != IMAGE_DIR_EAST && mImageDir != IMAGE_DIR_SOUTH) {
            return;
            //throw new Exception("obstacle image direction invalid type! - " + this.getId());
        }

        this.mImageDir = mImageDir;

        // redraw the obstacle
        invalidate();

        if (this.mainActivity != null) {
            this.mainActivity.sendMessageToAMD("{ \"faceObstacle:\" [" + this.getIdxX() + ", " + this.getIdxY() + ", " + mImageDir + ", " + this.getId() + "] }");
        }
    }

    public int getImageTargetId() {
        return mImageTargetId;
    }

    public void setImageTargetId(int mImageTargetId) {
        if (mImageTargetId < -1) {
            return;
        }

        this.mImageTargetId = mImageTargetId;

        // retrieve image by id
        if (mImageTargetId > -1) {
            int resId = getResources().getIdentifier("s" + this.getImageTargetId(), "drawable", getContext().getPackageName());
            Log.e("ObstacleView", "image detected! id: " + this.getImageTargetId() + ", resId: " + resId);
            setImageResource(resId);
        }

        // redraw the obstacle
        invalidate();
    }

    /** convert idx to axis on arena map */
    public Point getAxisFromIdx() {
        // return new Point(this.idxX + 1, ArenaView.ROWS - this.idxY);
        return new Point(this.idxX, ArenaView.ROWS - this.idxY - 1);
    }

    @Override
    public String toString() {
        Point axis = getAxisFromIdx();

        return "ObstacleView{" +
                "id=" + id +
                ", X=" + axis.x +
                ", Y=" + axis.y +
                ", imageDir=" + getImageDirStr() +
                '}';
    }
}
