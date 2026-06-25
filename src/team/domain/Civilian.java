package team.domain;

public class Civilian {

    public enum State {
        SEEKING_SHELTER, // בדרך למבנה
        ON_ROOF,         // עומד על גג הבניין ← חדש!
        HIDING,          // מתחבא בתוך המבנה
        FLEEING          // בורח בבהלה (כשהמבנה נפגע)
    }

    private int id;
    private double x;
    private double y;
    private double speed;
    private State state;
    private GroundAsset targetBuilding;
    private int direction; // 1 ימינה, -1 שמאלה

    // --- פיזיקת עפיפה ---
    private double velX = 0;
    private double velY = 0;
    private boolean isAirborne = false;

    private static final double GRAVITY  = 900.0; // px/sec²
    private static final double AIR_DRAG = 0.98;  // חיכוך אוויר לכל פריים

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

        // --- טיפול בעפיפה פיזיקלית ---
        if (isAirborne) {
            velY += GRAVITY * timeStep;   // כבידה
            velX *= AIR_DRAG;             // חיכוך אוויר
            x += velX * timeStep;
            y += velY * timeStep;

            // נחיתה על הקרקע
            if (y >= groundY) {
                y = groundY;
                isAirborne = false;
                velX *= 0.4; // בלימה בנחיתה
                velY = 0;
                // לאחר נחיתה — ממשיך לברוח על הקרקע
                this.direction = velX > 0 ? 1 : (velX < 0 ? -1 : (Math.random() > 0.5 ? 1 : -1));
            }
            return; // בזמן עפיפה לא מפעילים לוגיקה אחרת
        }

        // --- מצבי קרקע ---
        this.y = groundY;

        if (state == State.SEEKING_SHELTER) {
            if (targetBuilding != null) {
                double targetX = targetBuilding.getX() + targetBuilding.getWidth() / 2.0;
                if (Math.abs(x - targetX) < (speed * timeStep)) {
                    
                    if (Math.random() < 0.7) {
                        goToRoof();
                    } else {
                        state = State.HIDING;
                    }
                } else {
                    x += direction * (speed * timeStep);
                }
            } else {
                state = State.FLEEING;
            }

        } else if (state == State.ON_ROOF) {
            if (targetBuilding != null) {
                this.y = targetBuilding.getY();
                
                // מגדיר את גבולות ההליכה על הגג (10% מקצה לקצה כדי שלא ייפלו)
                double leftBound = targetBuilding.getX() + targetBuilding.getWidth() * 0.1;
                double rightBound = targetBuilding.getX() + targetBuilding.getWidth() * 0.9;
                
                // מזיז את האזרח במהירות איטית יותר (40% ממהירות הריצה שלו)
                this.x += direction * (speed * 0.4 * timeStep);
                
                // הופך כיוון אם הגיע לקצה הגג
                if (this.x < leftBound) {
                    this.x = leftBound;
                    this.direction = 1; // ימינה
                } else if (this.x > rightBound) {
                    this.x = rightBound;
                    this.direction = -1; // שמאלה
                }
            }

        } else if (state == State.FLEEING) {
            // כשהוא בורח מאש, הוא רץ מהר יותר (פי 1.5)
            x += direction * (speed * 1.5 * timeStep);
        }
    }

    // שולח את הדמות לגג הבניין
    public void goToRoof() {
        if (targetBuilding == null) return;
        this.state = State.ON_ROOF;
        // מיקום אקראי על פני רוחב הגג
        this.x = targetBuilding.getX()
                + targetBuilding.getWidth() * 0.1
                + Math.random() * targetBuilding.getWidth() * 0.8;
        this.y = targetBuilding.getY();
    }

    // פונקציה שמופעלת כשהמבנה של האזרח חוטף פגיעה — עפיפה באוויר!
    public void catchFireAndFlee() {
        if (this.state == State.HIDING) {
            // יוצא מהמבנה ובורח לצד אקראי
            this.state = State.FLEEING;
            this.direction = Math.random() > 0.5 ? 1 : -1;
            if (this.targetBuilding != null) {
                this.x = this.targetBuilding.getX() + this.targetBuilding.getWidth() / 2.0;
            }

        } else if (this.state == State.ON_ROOF) {
            // מי שעל הגג — עף באוויר!
            this.state = State.FLEEING;

            // מיקום התחלתי = גג הבניין
            if (this.targetBuilding != null) {
                this.x = this.targetBuilding.getX()
                        + this.targetBuilding.getWidth() * 0.2
                        + Math.random() * this.targetBuilding.getWidth() * 0.6;
                this.y = this.targetBuilding.getY();
            }

            // כוח פיצוץ: לצד אקראי וחזק למעלה
            double side = Math.random() > 0.5 ? 1.0 : -1.0;
            this.velX = side * (200 + Math.random() * 300); // 200–500 px/sec לצד
            this.velY = -(500 + Math.random() * 300);       // 500–800 px/sec למעלה
            this.isAirborne = true;
        }
    }

    public boolean isOutOfBounds(double worldWidth) {
        return x < -100 || x > worldWidth + 100;
    }

    // Getters עבור ה-UI
    public int getId()                    { return id; }
    public double getX()                  { return x; }
    public double getY()                  { return y; }
    public State getState()               { return state; }
    public GroundAsset getTargetBuilding(){ return targetBuilding; }
    public boolean isAirborne()           { return isAirborne; }
    public double getVelX()               { return velX; }

    public boolean isHiding() {
        return state == State.HIDING;
    }
}