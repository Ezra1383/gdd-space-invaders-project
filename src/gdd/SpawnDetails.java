package gdd;


public class SpawnDetails {
    public int frame;
    public String type;
    public int x;        // spawn/entry x (enemies enter from the right edge)
    public int y;
    public int targetX;  // home column an enemy flies to and holds at (Stage 5b)

    public SpawnDetails(int frame, String type, int x, int y) {
      this(frame, type, x, y, x); // default: hold where it spawns
    }

    public SpawnDetails(int frame, String type, int x, int y, int targetX) {
      this.frame = frame;
      this.type = type;
      this.x = x;
      this.y = y;
      this.targetX = targetX;
    }
}
