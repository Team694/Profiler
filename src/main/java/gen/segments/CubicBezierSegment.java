package gen.segments;

import gen.Vector;
import gen.Waypoint;

/**
 * @author Tahsin Ahmed
 * A segment that is generated through the cubic bezier equations.
 */
public class CubicBezierSegment extends Segment {
    /** A cubic bezier segment generator
     * used by the corresponding spline.  */
    public static class CubicBezierSegmentFactory implements Segment.SegmentFactory {
        /**
         * @param tightness A multiplier that controls how close the
         *                  control points are to the start/end of
         *                  the individual curve.
         * @param startwp the starting waypoint.
         * @param endwp the ending waypoint
         * @return A segment that connects the starting waypoint
         * to the ending waypoint witht he given tightness (this
         * will stay constant throughout the generation of the curve).
         */
        @Override
        public CubicBezierSegment getInstance(double tightness, Waypoint startwp, Waypoint endwp) {
            //taken from team 3641's method of calculating bezier curves found here:
            //https://github.com/JackToaster/FlyingToasters2018/blob/master/src/path_generation/Path.java
            Vector[] points = new Vector[4];
            points[0] = startwp;
            points[3] = endwp;

            double distance = startwp.distanceTo(endwp);
            double gplength = distance / 2 * tightness;
            Vector startOffset = Vector.PolarPoint(gplength, startwp.heading);
            Vector endOffset = Vector.PolarPoint(-gplength, endwp.heading);
            points[1] = startwp.offsetCartesian(startOffset.x, startOffset.y).toVector();
            points[2] = endwp.offsetCartesian(endOffset.x, endOffset.y).toVector();
            return null;
        }

    }

    /**
     * 3 is the order of the segment.
     * @param points the control and starting points.
     */
    public CubicBezierSegment(Vector... points) {
        super(3, points);
    }

    /**
     * @param alpha progression on curve.
     * @return the point on a curve (in vector form).
     */
    @Override
    public Vector getPoint(double alpha) {
        double x = points[0].x * Math.pow(1 - alpha, 3) + points[1].x * 3 * Math.pow(1 - alpha, 2) * alpha +
                points[2].x * 3 * (1 - alpha) * Math.pow(alpha, 2) + points[3].x * Math.pow(alpha, 3);
        double y = points[0].y * Math.pow(1 - alpha, 3) + points[1].y * 3 * Math.pow(1 - alpha, 2) * alpha +
                points[2].y * 3 * (1 - alpha) * Math.pow(alpha, 2) + points[3].y * Math.pow(alpha, 3);
        Vector point = new Vector(x, y);
        return point;
    }

    /**
     * @param alpha progression on curve.
     * @return a Vector that contains the x
     * and y components of the slope of the point.
     */
    @Override
    public Vector differentiateC(double alpha) {
        //uses equation for bezier derivatives: Σi=0,n Bn-1,i(t) * n * (Pi+1 - pi)
        //https://pomax.github.io/bezierinfo/#derivatives
        double dx = 3 * Math.pow(1 - alpha, 2) * (points[1].x - points[0].x) +
                6 * alpha * (1 - alpha) * (points[2].x - points[1].x) +
                3 * Math.pow(alpha, 2) * (points[3].x - points[2].x);
        double dy = 3 * Math.pow(1 - alpha, 2) * (points[1].y - points[0].y) +
                6 * alpha * (1 - alpha) * (points[2].y - points[1].y) +
                3 * Math.pow(alpha, 2) * (points[3].y - points[2].y);
        return new Vector(dx, dy);
    }

    /**
     * @param from a point on the curve.
     * @param to another point on the curve.
     * @return the arclength between these two points.
     */
    @Override
    public double integrate(double from, double to) {
        //uses 2 point Gauss Quadrature method to approximate arc length: ∫a,b f(x)dx = Σi=0,n Ci*f(xi)
        //https://pomax.github.io/bezierinfo/#arclength
        //https://www.youtube.com/watch?v=unWguclP-Ds&feature=BFa&list=PLC8FC40C714F5E60F&index=1
        //values found here: https://pomax.github.io/bezierinfo/legendre-gauss.html
        double[] weights = {1.0000000000000000, 1.0000000000000000};
        double[] abscissa = {-0.5773502691896257, 0.5773502691896257};
        double sum = 0;
        for(int i = 0; i < 3; i++) {
            double pt = ((to - from) / 2.0) * abscissa[i] + ((to + from) / 2.0);
            Vector d = differentiateC(pt);
            sum += weights[i] * Math.sqrt(d.x*d.x + d.y*d.y);
        }
        return ((to - from) / 2.0) * sum;
    }

}
