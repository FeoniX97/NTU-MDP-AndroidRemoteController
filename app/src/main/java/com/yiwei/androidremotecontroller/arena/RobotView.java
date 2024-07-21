package com.yiwei.androidremotecontroller.arena;

import android.content.Context;
import android.graphics.Point;
import android.support.v7.widget.AppCompatImageView;

import com.yiwei.androidremotecontroller.R;

public class RobotView extends AppCompatImageView {

    private final int coordX;
    private final int coordY;
    private final int idxX;
    private final int idxY;
    private int dirInt;
    private String dir;

    public RobotView(Context context, int coordX, int coordY, int idxX, int idxY, int dirInt) {
        super(context);

        this.coordX = coordX;
        this.coordY = coordY;
        this.idxX = idxX;
        this.idxY = idxY;

        setImageResource(R.drawable.robot);
        setDirInt(dirInt);
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

    public int getDirInt() {
        return dirInt;
    }

    public void setDirInt(int dirInt) {
        this.dirInt = dirInt;

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

        // rotate image according to dir
        setRotation(dirInt);
    }

    public String getDir() {
        return dir;
    }

    /** convert idx to axis on arena map */
    public Point getAxisFromIdx() {
        return new Point(this.idxX, ArenaView.ROWS - this.idxY - 1);
    }

    public int getDirIntForApi() {
        switch (this.dir) {
            case "N":
                return 0;
            case "E":
                return 2;
            case "S":
                return 4;
            case "W":
                return 6;
        }

        return 8;
    }
}
