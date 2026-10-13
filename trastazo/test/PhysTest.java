package com.frameboost.ragdoll;

public class PhysTest {
    static int fails;
    static void check(String n, boolean ok) { System.out.println((ok ? "PASS " : "FAIL ") + n); if (!ok) fails++; }
    static void run(Sim s, float secs) { int n = (int) (secs * 60); for (int i = 0; i < n; i++) s.update(1f / 60f); }

    static RB box(Sim s, float x, float y, float w, float h, float ang) {
        RB b = new RB(x, y, ang); b.add(Shape.box(w / 2, h / 2).mat(1f, 0.6f, 0.05f)); return s.add(b);
    }

    public static void main(String[] a) {
        // 1. a stack of crates stays put
        Sim s = new Sim(); s.floorY = 0;
        RB[] st = new RB[6];
        for (int i = 0; i < 6; i++) st[i] = box(s, 500, -30 - i * 60.5f, 60, 60, 0);
        run(s, 6);
        float drift = 0; for (RB b : st) drift = Math.max(drift, Math.abs(b.x - 500));
        System.out.printf("   stack: top y=%.1f drift=%.2f angle=%.3f%n", st[5].y, drift, st[5].a);
        check("six crates stack without toppling", drift < 6f && Math.abs(st[5].a) < 0.1f && st[5].y < -300f);

        // 2. circle rolls down nothing: rests on floor, and a ball rolls when pushed
        s = new Sim();
        RB ball = new RB(300, -40, 0); ball.add(Shape.circle(25).mat(1f, 0.8f, 0.2f)); s.add(ball);
        ball.vx = 400;
        run(s, 3);
        System.out.printf("   ball: x=%.0f vx=%.1f w=%.2f y=%.1f%n", ball.x, ball.vx, ball.w, ball.y);
        check("ball rolls without sliding (w*r ~ vx)", Math.abs(ball.w * 25 - ball.vx) < 40f && ball.x > 600);

        // 3. capsule lies still on the floor
        s = new Sim();
        RB cap = new RB(300, -60, 0.3f); cap.add(Shape.capsule(40, 8).mat(1f, 0.6f, 0.02f)); s.add(cap);
        run(s, 4);
        System.out.printf("   capsule: y=%.1f a=%.3f v=%.2f%n", cap.y, cap.a, Math.hypot(cap.vx, cap.vy));
        check("capsule settles flat", Math.abs(cap.y + 8) < 2f && Math.hypot(cap.vx, cap.vy) < 5f);

        // 4. box on box platform (static)
        s = new Sim();
        RB plat = new RB(500, -150, 0); plat.add(Shape.box(150, 10)); plat.makeStatic(); s.add(plat);
        RB crate = box(s, 450, -400, 60, 60, 0.4f);
        run(s, 4);
        System.out.printf("   crate on platform: y=%.1f a=%.3f%n", crate.y, crate.a);
        check("crate lands on the platform and settles", Math.abs(crate.y - (-150 - 10 - 30)) < 2f && Math.abs(Math.sin(crate.a * 2)) < 0.05f);

        // 5. X of planks: two planks crossing
        s = new Sim();
        RB p1 = box(s, 500, -8, 300, 16, 0);
        RB p2 = box(s, 500, -60, 300, 16, 0.0f);
        p2.x = 500; p2.a = 1.2f; p2.setAngle(1.2f);
        run(s, 4);
        System.out.printf("   planks: p1 y=%.1f p2 y=%.1f p2 a=%.2f%n", p1.y, p2.y, p2.a);
        check("planks do not tunnel through each other", Math.abs(p1.y + 8) < 10f);

        // 6. pendulum joint keeps its length
        s = new Sim();
        RB pivot = new RB(500, -500, 0).makeStatic(); pivot.add(Shape.circle(2)); s.add(pivot);
        RB bob = new RB(700, -500, 0); bob.add(Shape.circle(15).mat(2f, 0.5f, 0)); s.add(bob);
        s.add(Joint.pin(pivot, bob, 500, -500));
        float maxErr = 0;
        for (int i = 0; i < 360; i++) {
            s.update(1f / 60f);
            float d = (float) Math.hypot(bob.x - 500, bob.y + 500);
            maxErr = Math.max(maxErr, Math.abs(d - 200));
        }
        System.out.printf("   pendulum length error %.2f%n", maxErr);
        check("pendulum keeps its length", maxErr < 4f);

        // 7. revolute limit + muscle: arm holds a target angle against gravity
        s = new Sim();
        RB shoulder = new RB(500, -500, 0).makeStatic(); shoulder.add(Shape.circle(2)); s.add(shoulder);
        RB arm = new RB(540, -500, 0); arm.add(Shape.capsule(30, 6).mat(1f, 0.5f, 0)); s.add(arm);
        Joint j = Joint.pin(shoulder, arm, 510, -500);
        j.muscle(8f, 0.8f, 2e8f); j.target = 0.5f; j.limits(-1.5f, 1.5f);
        s.add(j);
        run(s, 3);
        System.out.printf("   arm angle=%.3f target=0.5%n", j.angle());
        check("muscle holds the arm near its target", Math.abs(j.angle() - 0.5f) < 0.25f);
        j.muscle = false;
        run(s, 3);
        check("limit stops the limp arm", j.angle() <= 1.5f + 0.15f && j.angle() > 0.8f);

        // 8. weld joint holds two crates together and can break
        s = new Sim();
        RB w1 = box(s, 400, -30, 60, 60, 0), w2 = box(s, 460, -30, 60, 60, 0);
        Joint wj = Joint.weld(w1, w2, 430, -30); s.add(wj);
        w1.vx = 600;
        run(s, 3);
        System.out.printf("   weld: gap=%.1f%n", w2.x - w1.x);
        check("weld keeps crates together", Math.abs((w2.x - w1.x) - 60) < 6f);

        // 9. motor wheel drives a plank (car): wheel with motor on a chassis moves
        s = new Sim();
        RB chassis = box(s, 500, -62, 140, 16, 0);
        RB wl = new RB(450, -25, 0); wl.add(Shape.circle(25).mat(1f, 1.2f, 0.05f).grp(7)); s.add(wl);
        RB wr = new RB(550, -25, 0); wr.add(Shape.circle(25).mat(1f, 1.2f, 0.05f).grp(7)); s.add(wr);
        chassis.shapes.get(0).group = 7;
        Joint m1 = Joint.pin(chassis, wl, 450, -25); m1.motor = true; m1.motorSpeed = 10f; m1.motorMax = 2e7f; s.add(m1);
        Joint m2 = Joint.pin(chassis, wr, 550, -25); m2.motor = true; m2.motorSpeed = 10f; m2.motorMax = 2e7f; s.add(m2);
        run(s, 4);
        System.out.printf("   car x=%.0f%n", chassis.x);
        check("motorised car drives forward", chassis.x > 900);

        // 10. many bodies speed
        s = new Sim();
        java.util.Random r = new java.util.Random(3);
        for (int i = 0; i < 150; i++) {
            if (i % 3 == 0) box(s, 200 + r.nextInt(1500), -100 - r.nextInt(1500), 40, 40, r.nextFloat());
            else if (i % 3 == 1) { RB c = new RB(200 + r.nextInt(1500), -100 - r.nextInt(1500), r.nextFloat()); c.add(Shape.capsule(25, 6)); s.add(c); }
            else { RB c = new RB(200 + r.nextInt(1500), -100 - r.nextInt(1500), 0); c.add(Shape.circle(18)); s.add(c); }
        }
        long t0 = System.nanoTime();
        run(s, 10);
        double ms = (System.nanoTime() - t0) / 1e6 / 600.0;
        System.out.printf("   150 bodies: %.2f ms per 60 Hz frame (2 steps)%n", ms);
        check("150 bodies run fast enough", ms < 8.0);
        float maxV = 0; for (RB b : s.bodies) maxV = Math.max(maxV, (float) Math.hypot(b.vx, b.vy));
        check("pile comes to rest (max v=" + (int) maxV + ")", maxV < 60f);

        System.out.println(fails == 0 ? "PHYS ALL PASS" : "PHYS FAILURES " + fails);
    }
}
