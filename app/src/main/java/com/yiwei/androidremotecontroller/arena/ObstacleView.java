package com.yiwei.androidremotecontroller.arena;

import android.content.ClipData;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.support.annotation.Nullable;
import android.support.v7.widget.PopupMenu;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

import com.yiwei.androidremotecontroller.R;

public class ObstacleView extends View {

    private static final char IMAGE_DIR_TOP = 't';
    private static final char IMAGE_DIR_LEFT = 'l';
    private static final char IMAGE_DIR_RIGHT = 'r';
    private static final char IMAGE_DIR_BOTTOM = 'b';
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
    private final Paint mRedPaintBrushFill;
    private final Rect mRect;
    private final Rect mRectTopBorder;
    private final Rect mRectLeftBorder;
    private final Rect mRectRightBorder;
    private final Rect mRectBottomBorder;

    private char mImageDir = IMAGE_DIR_NONE;

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
        size = attrArr.getInt(R.styleable.ObstacleView_size, 45);

        attrArr.recycle();

        // legend obstacle does not have index and image direction border
        mWhitePaintBrushFill = null;
        mRedPaintBrushFill = null;
        mRectTopBorder = mRectLeftBorder = mRectRightBorder = mRectBottomBorder = null;

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
        mRectRightBorder.set(size - 8, 0, size, size);
        mRectBottomBorder.set(0, size - 8, size, size);

        setupPopupMenu();
        setupDraggable();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        canvas.drawRect(mRect, mBlackPaintBrushFill);

        if (!isLegend()) {
            // draw index
            canvas.drawText(String.valueOf(this.id), (float) this.size / 2 - 7 - (this.id > 9 ? 5 : 0), (float) this.size / 2 + 2, this.mWhitePaintBrushFill);

            // draw image direction border if exists
            if (getImageDir() != IMAGE_DIR_NONE) {
                switch (getImageDir()) {
                    case IMAGE_DIR_TOP:
                        canvas.drawRect(mRectTopBorder, mRedPaintBrushFill);
                        break;
                    case IMAGE_DIR_LEFT:
                        canvas.drawRect(mRectLeftBorder, mRedPaintBrushFill);
                        break;
                    case IMAGE_DIR_RIGHT:
                        canvas.drawRect(mRectRightBorder, mRedPaintBrushFill);
                        break;
                    case IMAGE_DIR_BOTTOM:
                        canvas.drawRect(mRectBottomBorder, mRedPaintBrushFill);
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

            popupMenu.setOnMenuItemClickListener(menuItem -> {
                // Toast message on menu item clicked
                Log.e("Popup Menu",  v.getId() + ": " + menuItem.getTitle());
                try {
                    switch (menuItem.getItemId()) {
                        case R.id.top:
                            setImageDir(IMAGE_DIR_TOP);
                            break;
                        case R.id.left:
                            setImageDir(IMAGE_DIR_LEFT);
                            break;
                        case R.id.right:
                            setImageDir(IMAGE_DIR_RIGHT);
                            break;
                        case R.id.bottom:
                            setImageDir(IMAGE_DIR_BOTTOM);
                            break;
                        case R.id.clear:
                            setImageDir(IMAGE_DIR_NONE);
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

    public void setImageDir(char mImageDir) throws Exception {
        if (mImageDir != IMAGE_DIR_NONE && mImageDir != IMAGE_DIR_TOP && mImageDir != IMAGE_DIR_LEFT && mImageDir != IMAGE_DIR_RIGHT && mImageDir != IMAGE_DIR_BOTTOM) {
            throw new Exception("obstacle image direction invalid type! - " + this.getId());
        }

        this.mImageDir = mImageDir;

        // redraw the obstacle
        invalidate();
    }
}
