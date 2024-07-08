package com.yiwei.androidremotecontroller;

public class MessageJSON {

    /** robot -> app, the status of the robot, custom message, for c4 */
    private String status;

    /**  */
    private char move;

    /** robot -> app, send when initial BT connected<br />
     * for future direct connection */
    private static class BT {

        /** optional */
        private String name;

        /** BT hardware address */
        private String addr;

    }

    /** robot -> app, send whenever robot location/direction updated */
    private static class Robot {

        /** the current x coordinate of the robot on map */
        private int x;

        /** the current y coordinate of the robot on map */
        private int y;

        /** the direction of robot is facing: "n"orth, "s"outh, "e"ast, "w"est */
        private char dir;

    }

}
