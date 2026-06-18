package team.domain;

public class Civilian {
    
    public enum State {
        SEEKING_SHELTER, // בדרך למבנה
        HIDING,          // מתחבא בתוך המבנה
        FLEEING          // בורח בבהלה (כשהמבנה נפגע)
    }

    private int id;
    private double x;
    private double y;
    private double speed;
    private State state;
    private GroundAsset targetBuilding;
    private int direction; // 1 ימינה, 1- שמאלה

    // בנאי דמה ריק לחלוטין עבור התוקף
    public Civilian() {}

    public Civilian(int id, double startX, GroundAsset targetBuilding) {
        this.id = id;
        this.x = startX;
        this.targetBuilding = targetBuilding;
        // מהירות אקראית כדי שלא כולם ירוצו באותו קצב
        this.speed = 50.0 + Math.random() * 50.0; 
        this.state = State.SEEKING_SHELTER;
        
        // חישוב כיוון הריצה בהתאם למיקום המבנה
        if (targetBuilding != null) {
            double targetX = targetBuilding.getX() + targetBuilding.getWidth() / 2.0;
            this.direction = (targetX > this.x) ? 1 : -1;
        } else {
            this.direction = Math.random() > 0.5 ? 1 : -1;
        }
    }

    // פונקציה זו תיקרא בכל חלקיק שנייה מתוך לולאת המשחק
    public void update(double timeStep, int groundY) {
        this.y = groundY;
        
        if (state == State.SEEKING_SHELTER) {
            if (targetBuilding != null) {
                double targetX = targetBuilding.getX() + targetBuilding.getWidth() / 2.0;
                // בדיקה אם האזרח הגיע למרכז המבנה
                if (Math.abs(x - targetX) < (speed * timeStep)) {
                    state = State.HIDING;
                } else {
                    x += direction * (speed * timeStep);
                }
            } else {
                state = State.FLEEING;
            }
        } else if (state == State.FLEEING) {
            // כשהוא בורח מאש, הוא רץ מהר יותר (פי 1.5)
            x += direction * (speed * 1.5 * timeStep);
        }
    }

    // פונקציה שמופעלת כשהמבנה של האזרח חוטף פגיעה
    public void catchFireAndFlee() {
        if (this.state == State.HIDING) {
            this.state = State.FLEEING;
            this.direction = Math.random() > 0.5 ? 1 : -1; // בורח לכיוון אקראי
            if (this.targetBuilding != null) {
                this.x = this.targetBuilding.getX() + this.targetBuilding.getWidth() / 2.0;
            }
        }
    }

    public boolean isOutOfBounds(double worldWidth) {
        return x < -100 || x > worldWidth + 100;
    }

    // Getters עבור ה-UI כדי שיוכל לצייר אותו
    public int getId() { return id; }
    public double getX() { return x; }
    public double getY() { return y; }
    public State getState() { return state; }
    public GroundAsset getTargetBuilding() { return targetBuilding; }
}   