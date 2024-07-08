using System;
namespace ScriptNs
{
    public class ScriptContainer
    {
        // This will send the arena information when there is an add or remove of obstacle, and send the robot position when its position and direction change
        // Information is in JSON format.

        // {"robotPosition": [x,y,direction]} 
        // where x and y are x and y positions with origin at the top left increasing towards bottom right, 
        // direction is the angle facing north at 0, increasing clockwise

        public static string MainScript(int[,] gridLayout, int[] robotPosition, bool posTgridF, bool addObstacle, int[] obstaclePosition)
        {
            string stringToSend = "";
            if (posTgridF)
            {
                // stringToSend = @"{""robotPosition"" : [" + robotPosition[0] + ", " + robotPosition[1] + ", " + robotPosition[2] + "]}";

                char dir = 'x';
                switch (robotPosition[2]) {
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

                stringToSend = "robot:" + robotPosition[0] + "," + robotPosition[1] + "," + dir;
            }
            else
            {
                stringToSend = @"{""obstacle"" : [" + obstaclePosition[0] + ", " + obstaclePosition[1] + ", " + Convert.ToInt32(addObstacle) + "]}";

                // var width = gridLayout.GetLength(0);
                // var height = gridLayout.GetLength(1);
                // var bitNumber = 0;
                // var hexString = "";
                // var binaryString = "";
                // for (var h = 0; h < height; h++)
                // {
                //     for (var w = 0; w < width; w++)
                //     {
                //         bitNumber += 1;
                //         binaryString += gridLayout[w, h];
                //         if (bitNumber % 4 == 0)
                //         {
                //             hexString += Convert.ToInt64(binaryString, 2).ToString("x");
                //             binaryString = "";
                //             bitNumber = 0;
                //         }
                //     }
                // }
                // if (!binaryString.Equals(""))
                // {
                //     for (var i = bitNumber; i < 4; i++)
                //         binaryString += 0;
                //     hexString += Convert.ToInt64(binaryString, 2).ToString("x");
                // }

                // stringToSend = @"{""grid"" : """ + hexString + "\"}";
            }

            return stringToSend;
        }


    }

}