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
import android.widget.RelativeLayout;

public class ArenaView extends RelativeLayout {

    private final Paint mBlackPaintBrushFill;
    private final Paint mBluePaintBrushFill;
    private final int ROWS = 15;
    private final int COLS = 14;
    private final Rect[][] mRects = new Rect[ROWS][COLS];
    private final Point[] mCoordsY = new Point[ROWS];
    private final Point[] mCoordsX = new Point[COLS];
    private final int LEFT_OFFSET = 30;
    private final int MARGIN = 5;
    private final int SIZE = 50;

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
                int left = n * SIZE + MARGIN + LEFT_OFFSET;
                int top = m * SIZE + MARGIN;
                int right = n * SIZE + SIZE + LEFT_OFFSET;
                int bottom = m * SIZE + SIZE;

                mRects[m][n] = new Rect();
                mRects[m][n].set(left, top, right, bottom);

                // save coordinates X
                if (m == 0)
                    mCoordsX[n] = new Point(n * SIZE + SIZE, SIZE * ROWS + MARGIN + 20);
            }

            // save coordinates Y
            mCoordsY[m] = new Point(MARGIN, m * SIZE + (MARGIN + SIZE / 2 + 2));
        }

        RelativeLayout.LayoutParams params1 = new RelativeLayout.LayoutParams(50, 50);
        params1.leftMargin = MARGIN + LEFT_OFFSET;
        params1.topMargin = MARGIN;

        RelativeLayout.LayoutParams params2 = new RelativeLayout.LayoutParams(50, 50);
        params2.leftMargin = SIZE + MARGIN + LEFT_OFFSET;
        params2.topMargin = SIZE + MARGIN;

        ArenaTileView tileView1 = new ArenaTileView(getContext(), 0, 0, 0, 0, 50);
        ArenaTileView tileView2 = new ArenaTileView(getContext(), 0, 0, 0, 0, 50);

        addView(tileView1, params1);
        addView(tileView2, params2);
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
                    if (n < 10) {
                        // add some offset to digit if x < 10
                        point.x += 3;
                    }
                    canvas.drawText(String.valueOf(n), point.x, point.y, mBlackPaintBrushFill);
                }
            }

            // draw coordinates Y
            Point point = mCoordsY[m];
            canvas.drawText(String.valueOf(m), point.x, point.y, mBlackPaintBrushFill);
        }
    }
}