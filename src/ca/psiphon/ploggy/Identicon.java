/*
 * Copyright (c) 2013, Psiphon Inc.
 * All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

/*

Original: com.docuverse.identicon.NineBlockIdenticonRenderer.java

(The MIT License)

Copyright (c) 2007-2012 Don Park <donpark@docuverse.com>

Permission is hereby granted, free of charge, to any person obtaining
a copy of this software and associated documentation files (the
'Software'), to deal in the Software without restriction, including
without limitation the rights to use, copy, modify, merge, publish,
distribute, sublicense, and/or sell copies of the Software, and to
permit persons to whom the Software is furnished to do so, subject to
the following conditions:

The above copyright notice and this permission notice shall be
included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED 'AS IS', WITHOUT WARRANTY OF ANY KIND,
EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY
CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT,
TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
*/

package ca.psiphon.ploggy;

//TODO: http://stackoverflow.com/questions/3534642/how-to-map-javas-affinetransform-to-androids-matrix
//TODO: 16-panel version: http://scott.sherrillmix.com/blog/blogger/wp_identicon/
//TODO: http://scott.sherrillmix.com/blog/blogger/wp_monsterid/

/*
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;

public class Identicon {

	// Each patch is a polygon created from a list of vertices on a 5 by 5 grid.
    // Vertices are numbered from 0 to 24, starting from top-left corner of the
    // grid, moving left to right and top to bottom.
	
    private static final int DEFAULT_PATCH_SIZE = 20;

    private static final int PATCH_CELLS = 4;

    private static final int PATCH_GRIDS = PATCH_CELLS + 1;

    private static final byte PATCH_SYMMETRIC = 1;

    private static final byte PATCH_INVERTED = 2;

    private static final byte[] patch0 = { 0, 4, 24, 20, 0 };

    private static final byte[] patch1 = { 0, 4, 20, 0 };

    private static final byte[] patch2 = { 2, 24, 20, 2 };

    private static final byte[] patch3 = { 0, 2, 20, 22, 0 };

    private static final byte[] patch4 = { 2, 14, 22, 10, 2 };

    private static final byte[] patch5 = { 0, 14, 24, 22, 0 };

    private static final byte[] patch6 = { 2, 24, 22, 13, 11, 22, 20, 2 };

    private static final byte[] patch7 = { 0, 14, 22, 0 };

    private static final byte[] patch8 = { 6, 8, 18, 16, 6 };

    private static final byte[] patch9 = { 4, 20, 10, 12, 2, 4 };

    private static final byte[] patch10 = { 0, 2, 12, 10, 0 };

    private static final byte[] patch11 = { 10, 14, 22, 10 };

    private static final byte[] patch12 = { 20, 12, 24, 20 };

    private static final byte[] patch13 = { 10, 2, 12, 10 };

    private static final byte[] patch14 = { 0, 2, 10, 0 };

    private static final byte[] patchTypes[] = { patch0, patch1, patch2,
            patch3, patch4, patch5, patch6, patch7, patch8, patch9, patch10,
            patch11, patch12, patch13, patch14, patch0 };

    private static final byte patchFlags[] = { PATCH_SYMMETRIC, 0, 0, 0,
            PATCH_SYMMETRIC, 0, 0, 0, PATCH_SYMMETRIC, 0, 0, 0, 0, 0, 0,
            PATCH_SYMMETRIC + PATCH_INVERTED };

    private static int centerPatchTypes[] = { 0, 4, 8, 15 };

    private int patchSize;

    private Shape[] patchShapes;

    // used to center patch shape at origin because shape rotation works
    // correctly.
    private int patchOffset;

    private int backgroundColor = Color.WHITE;

    public NineBlockIdenticonRenderer() {
        setPatchSize(DEFAULT_PATCH_SIZE);
    }

    // Returns the size in pixels at which each patch will be rendered before
    // they are scaled down to requested identicon size.
    public int getPatchSize() {
        return patchSize;
    }

    // Set the size in pixels at which each patch will be rendered before they
    // are scaled down to requested identicon size. Default size is 20 pixels
    // which means, for 9-block identicon, a 60x60 image will be rendered and
    // scaled down.
    public void setPatchSize(int size) {
        this.patchSize = size;
        this.patchOffset = patchSize / 2; // used to center patch shape at origin.
        int scale = patchSize / PATCH_CELLS;
        this.patchShapes = new Polygon[patchTypes.length];
        for (int i = 0; i < patchTypes.length; i++) {
            Polygon patch = new Polygon();
            byte[] patchVertices = patchTypes[i];
            for (int j = 0; j < patchVertices.length; j++) {
                int v = (int) patchVertices[j];
                int vx = (v % PATCH_GRIDS * scale) - patchOffset;
                int vy = (v / PATCH_GRIDS * scale) - patchOffset;
                patch.addPoint(vx, vy);
            }
            this.patchShapes[i] = patch;
        }
    }

    // Size of the returned identicon image is determined by patchSize set using
    // setPatchSize. Since a 9-block identicon consists of 3x3 patches,
    // width and height will be 3 times the patch size.
    public BufferedImage render(int code, int size) {
        return renderQuilt(code, size);
    }

    protected BufferedImage renderQuilt(int code, int size) {
        // -------------------------------------------------
        // PREPARE
        //

        // decode the code into parts
        // bit 0-1: middle patch type
        // bit 2: middle invert
        // bit 3-6: corner patch type
        // bit 7: corner invert
        // bit 8-9: corner turns
        // bit 10-13: side patch type
        // bit 14: side invert
        // bit 15: corner turns
        // bit 16-20: blue color component
        // bit 21-26: green color component
        // bit 27-31: red color component
        int middleType = centerPatchTypes[code & 0x3];
        boolean middleInvert = ((code >> 2) & 0x1) != 0;
        int cornerType = (code >> 3) & 0x0f;
        boolean cornerInvert = ((code >> 7) & 0x1) != 0;
        int cornerTurn = (code >> 8) & 0x3;
        int sideType = (code >> 10) & 0x0f;
        boolean sideInvert = ((code >> 14) & 0x1) != 0;
        int sideTurn = (code >> 15) & 0x3;
        int blue = (code >> 16) & 0x01f;
        int green = (code >> 21) & 0x01f;
        int red = (code >> 27) & 0x01f;

        // color components are used at top of the range for color difference
        // use white background for now.
        // TODO: support transparency.
        Color fillColor = new Color(red << 3, green << 3, blue << 3);

        // outline shapes with a noticeable color (complementary will do) if
        // shape color and background color are too similar (measured by color
        // distance).
        Color strokeColor = null;
        if (getColorDistance(fillColor, backgroundColor) < 32.0f)
            strokeColor = getComplementaryColor(fillColor);

        // -------------------------------------------------
        // RENDER AT SOURCE SIZE
        //

        int sourceSize = patchSize * 3;
        BufferedImage sourceImage = new BufferedImage(sourceSize, sourceSize,
                BufferedImage.TYPE_INT_RGB);
        Graphics2D g = sourceImage.createGraphics();

        // middle patch
        drawPatch(g, patchSize, patchSize, middleType, 0, middleInvert,
                fillColor, strokeColor);

        // side patches, starting from top and moving clock-wise
        drawPatch(g, patchSize, 0, sideType, sideTurn++, sideInvert, fillColor,
                strokeColor);
        drawPatch(g, patchSize * 2, patchSize, sideType, sideTurn++,
                sideInvert, fillColor, strokeColor);
        drawPatch(g, patchSize, patchSize * 2, sideType, sideTurn++,
                sideInvert, fillColor, strokeColor);
        drawPatch(g, 0, patchSize, sideType, sideTurn++, sideInvert, fillColor,
                strokeColor);

        // corner patches, starting from top left and moving clock-wise
        drawPatch(g, 0, 0, cornerType, cornerTurn++, cornerInvert, fillColor,
                strokeColor);
        drawPatch(g, patchSize * 2, 0, cornerType, cornerTurn++, cornerInvert,
                fillColor, strokeColor);
        drawPatch(g, patchSize * 2, patchSize * 2, cornerType, cornerTurn++,
                cornerInvert, fillColor, strokeColor);
        drawPatch(g, 0, patchSize * 2, cornerType, cornerTurn++, cornerInvert,
                fillColor, strokeColor);

        g.dispose();

        // -------------------------------------------------
        // SCALE TO TARGET SIZE
        //
        // Bicubic algorithm is used for quality scaling

        BufferedImage targetImage = new BufferedImage(size, size,
                BufferedImage.TYPE_INT_RGB);
        g = targetImage.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.drawImage(sourceImage, 0, 0, size, size, null);
        g.dispose();

        return targetImage;
    }

    private void drawPatch(Graphics2D g, int x, int y, int patch, int turn,
            boolean invert, Color fillColor, Color strokeColor) {
        assert patch >= 0;
        assert turn >= 0;
        patch %= patchTypes.length;
        turn %= 4;
        if ((patchFlags[patch] & PATCH_INVERTED) != 0)
            invert = !invert;

        // paint background
        g.setBackground(invert ? fillColor : backgroundColor);
        g.clearRect(x, y, patchSize, patchSize);

        // offset and rotate coordinate space by patch position (x, y) and
        // 'turn' before rendering patch shape
        AffineTransform saved = g.getTransform();
        g.translate(x + patchOffset, y + patchOffset);
        g.rotate(Math.toRadians(turn * 90));

        // if stroke color was specified, apply stroke
        // stroke color should be specified if fore color is too close to the
        // back color.
        if (strokeColor != null) {
            g.setColor(strokeColor);
            g.draw(patchShapes[patch]);
        }

        // render rotated patch using fore color (back color if inverted)
        g.setColor(invert ? backgroundColor : fillColor);
        g.fill(patchShapes[patch]);

        // restore rotation
        g.setTransform(saved);
    }

    private float getColorDistance(int c1, int c2) {
        float dx = Color.red(c1) - Color.red(c2);
        float dy = Color.green(c1) - Color.green(c2);
        float dz = Color.blue(c1) - Color.blue(c2);
        return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private int getComplementaryColor(int color) {
        return color ^ 0x00FFFFFF;
    }
}
*/
