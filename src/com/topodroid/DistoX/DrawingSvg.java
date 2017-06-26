/** @file DrawingSvg.java
 *
 * @author marco corvi
 * @date mar 2013
 *
 * @brief TopoDroid drawing: dxf export
 * --------------------------------------------------------
 *  Copyright This sowftare is distributed under GPL-3.0 or later
 *  See the file COPYING.
 * --------------------------------------------------------
 */
package com.topodroid.DistoX;


import java.util.Locale;

import java.util.List;
// import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;

// import java.io.FileWriter;
import java.io.StringWriter;
import java.io.PrintWriter;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

import android.graphics.RectF;

import android.util.Log;

class DrawingSvg
{
  private static final float grad2rad = TDMath.GRAD2RAD;

  private static void printSvgGrid( BufferedWriter out, List<DrawingPath> grid, String color, float opacity, float xoff, float yoff )
  {
    if ( grid != null && grid.size() > 0 ) {
      StringWriter sw = new StringWriter();
      PrintWriter pw  = new PrintWriter(sw);
      pw.format(Locale.US, "<g style=\"fill:none;stroke-opacity:%.1f;stroke-width=1;stroke:#666666\" >\n", opacity );
      for ( DrawingPath p : grid ) {
        pw.format(Locale.US, "  <path stroke-width=\"1\" stroke=\"#%s\" d=\"", color );
        pw.format(Locale.US, "M %.2f %.2f",  xoff+p.x1, yoff+p.y1 );
        pw.format(Locale.US, " L %.2f %.2f", xoff+p.x2, yoff+p.y2 );
        pw.format("\" />\n");
      }
      pw.format("</g>\n");
      try {
        out.write( sw.getBuffer().toString() );
        out.flush();
      } catch ( IOException e ) {
        TDLog.Error( "SVG grid io-exception " + e.getMessage() );
      }
    }
  }

  static private String pathToColor( DrawingPath path )
  {
    int color = path.color();
    int red = ( color >> 16 ) & 0xff;
    int grn = ( color >>  8 ) & 0xff;
    int blu = ( color       ) & 0xff;
    if ( red > 0xcc && grn > 0xcc && blu > 0xcc ) return "#cccccc"; // cut-off white
    return String.format( "#%02x%02x%02x", red, grn, blu );
  }

  static void write( BufferedWriter out, DistoXNum num, DrawingCommandManager plot, long type )
  {
    String wall_group = BrushManager.getLineGroup( BrushManager.mLineLib.mLineWallIndex );

    int handle = 0;
    RectF bbox = DrawingUtil.getBoundingBox( plot );
    float xmin = bbox.left;
    float xmax = bbox.right;
    float ymin = bbox.top;
    float ymax = bbox.bottom;
    int dx = (int)(xmax - xmin);
    int dy = (int)(ymax - ymin);
    if ( dx > 200 ) dx = 200;
    if ( dy > 200 ) dy = 200;
    xmin -= dx;  xmax += dx;
    ymin -= dy;  ymax += dy;
    float xoff = 0; // xmin; // offset
    float yoff = 0; // ymin;
    int width = (int)((xmax - xmin));
    int height = (int)((ymax - ymin));

    try {
      if ( TDSetting.mSvgInHtml ) { // SVG_IN_HTML
        out.write("<!DOCTYPE html>\n<html>\n<body>\n");
      }

      // header
      out.write( "<svg width=\"" + width + "\" height=\"" + height + "\"\n" );
      out.write( "   xmlns:svg=\"http://www.w3.org/2000/svg\"\n");
      out.write( "   xmlns=\"http://www.w3.org/2000/svg\" >\n" );
      out.write( "<!-- SVG created by TopoDroid v. " + TopoDroidApp.VERSION + " -->\n" );
      out.write( "  <defs>\n");
      out.write( "    <marker id=\"Triangle\" viewBox=\"0 0 10 10\" refX=\"0\" refY=\"5\" \n");
      out.write( "      markerUnits=\"strokeWidth\" markerWidth=\"4\" markerHeight=\"3\" orient=\"auto\" >\n");
      out.write( "      <path d=\"M 0 0 L 10 5 L 0 10 z\" />\n");
      out.write( "    </marker>\n"); 
      out.write( "  </defs>\n");

      out.write( "<g transform=\"translate(" + (int)(-xmin) + "," + (int)(-ymin) + ")\" >\n" );

      // ***** FIXME TODO POINT SYMBOLS
      // {
      //   // // 8 layer (0), 2 block name,
      //   for ( int n = 0; n < BrushManager.mPointLib.mSymbolNr; ++ n ) {
      //     SymbolPoint pt = (SymbolPoint)BrushManager.mPointLib.getSymbolByIndex(n);

      //     int block = 1+n; // block_name = 1 + therion_code
      //     writeString( out, 8, "POINT" );
      //     writeComment( out, pt.mName );
      //     writeInt( out, 2, block );
      //     writeInt( out, 70, 64 );
      //     writeString( out, 10, "0.0" );
      //     writeString( out, 20, "0.0" );
      //     writeString( out, 30, "0.0" );

      //     out.write( pt.getDxf() );
      //   }
      // }
      
      {
        float SCALE_FIX = DrawingUtil.SCALE_FIX;

        // centerline data
        if ( PlotInfo.isSketch2D( type ) ) { 
          if ( TDSetting.mSvgGrid ) {
            printSvgGrid( out, plot.GetGrid1(),   "999999", 0.4f, xoff, yoff );
            printSvgGrid( out, plot.GetGrid10(),  "666666", 0.6f, xoff, yoff );
            printSvgGrid( out, plot.GetGrid100(), "333333", 0.8f, xoff, yoff );
          }
          // FIXME OK PROFILE
          out.write("<g style=\"fill:none;stroke-opacity:0.6;stroke:red\" >\n");
          for ( DrawingPath sh : plot.getLegs() ) {
            DBlock blk = sh.mBlock;
            if ( blk == null ) continue;

            StringWriter sw4 = new StringWriter();
            PrintWriter pw4  = new PrintWriter(sw4);
            // if ( sh.mType == DrawingPath.DRAWING_PATH_FIXED ) {
              NumStation f = num.getStation( blk.mFrom );
              NumStation t = num.getStation( blk.mTo );
 
              pw4.format("  <path stroke-width=\"1\" stroke=\"black\" d=\"");
              if ( type == PlotInfo.PLOT_PLAN ) {
                float x  = xoff + DrawingUtil.toSceneX( f.e ); 
                float y  = yoff + DrawingUtil.toSceneY( f.s );
                float x1 = xoff + DrawingUtil.toSceneX( t.e );
                float y1 = yoff + DrawingUtil.toSceneY( t.s );
                pw4.format(Locale.US, "M %.2f %.2f L %.2f %.2f\" />\n", x, y, x1, y1 );
              } else if ( PlotInfo.isProfile( type ) ) { // FIXME OK PROFILE
                float x  = xoff + DrawingUtil.toSceneX( f.h );
                float y  = yoff + DrawingUtil.toSceneY( f.v );
                float x1 = xoff + DrawingUtil.toSceneX( t.h );
                float y1 = yoff + DrawingUtil.toSceneY( t.v );
                pw4.format(Locale.US, "M %.2f %.2f L %.2f %.2f\" />\n", x, y, x1, y1 );
              }
            // }
            out.write( sw4.getBuffer().toString() );
            out.flush();
          }
          for ( DrawingPath sh : plot.getSplays() ) {
            DBlock blk = sh.mBlock;
            if ( blk == null ) continue;

            StringWriter sw41 = new StringWriter();
            PrintWriter pw41  = new PrintWriter(sw41);
            // // if ( sh.mType == DrawingPath.DRAWING_PATH_SPLAY ) {
            //   NumStation f = num.getStation( blk.mFrom );
            //   pw41.format("  <path stroke-width=\"1\" stroke=\"grey\" d=\"");
            //   float dh = blk.mLength * (float)Math.cos( blk.mClino * grad2rad )*SCALE_FIX;
            //   if ( type == PlotInfo.PLOT_PLAN ) {
            //     float x = xoff + DrawingUtil.toSceneX( f.e ); 
            //     float y = yoff + DrawingUtil.toSceneY( f.s );
            //     float de =   dh * (float)Math.sin( blk.mBearing * grad2rad);
            //     float ds = - dh * (float)Math.cos( blk.mBearing * grad2rad);
            //     pw41.format(Locale.US, "M %.2f %.2f L %.2f %.2f\" />\n", x, y, x + de, (y+ds) );
            //   } else if ( PlotInfo.isProfile( type ) ) { // FIXME OK PROFILE
            //     float x = xoff + DrawingUtil.toSceneX( f.h );
            //     float y = yoff + DrawingUtil.toSceneY( f.v );
            //     float dv = - blk.mLength * (float)Math.sin( blk.mClino * grad2rad )*SCALE_FIX;
            //     int ext = blk.getReducedExtend();
            //     pw41.format(Locale.US, "M %.2f %.2f L %.2f %.2f\" />\n", x, y, x+dh*ext, (y+dv) );
            //   }
            // // }
            pw41.format("  <path stroke-width=\"1\" stroke=\"grey\" d=\"");
            pw41.format(Locale.US, "M %.2f %.2f L %.2f %.2f\" />\n", xoff+sh.x1, yoff+sh.y1, xoff+sh.x2, yoff+sh.y2 );

            out.write( sw41.getBuffer().toString() );
            out.flush();
          }
          out.write("</g>\n");
        }

        // FIXME station scale is 0.3
        float POINT_SCALE = 10.0f;
        for ( ICanvasCommand cmd : plot.getCommands() ) {
          if ( cmd.commandType() != 0 ) continue;
          DrawingPath path = (DrawingPath)cmd;
          String color_str = pathToColor( path );
          StringWriter sw5 = new StringWriter();
          PrintWriter pw5  = new PrintWriter(sw5);
          if ( path.mType == DrawingPath.DRAWING_PATH_STATION ) {
            toSvg( pw5, (DrawingStationPath)path, xoff, yoff );
          } else if ( path.mType == DrawingPath.DRAWING_PATH_LINE ) {
            toSvg( pw5, (DrawingLinePath)path, color_str, xoff, yoff );
          } else if ( path.mType == DrawingPath.DRAWING_PATH_AREA ) {
            toSvg( pw5, (DrawingAreaPath) path, color_str, xoff, yoff );
          } else if ( path.mType == DrawingPath.DRAWING_PATH_POINT ) {
            DrawingPointPath point = (DrawingPointPath)path;
            if ( point.mPointType == BrushManager.mPointLib.mPointSectionIndex ) {
              float xx = xoff+point.cx;
              float yy = yoff+point.cy;
              pw5.format(Locale.US, "<circle cx=\"%.2f\" cy=\"%.2f\" r=\"3\" ", xx, yy );
              pw5.format(" style=\"fill:grey;stroke:black;stroke-width:0.3\" />\n");

              // pw5.format(Locale.US, "<g transform=\"translate(%.2f,%.2f)\" >\n", xx, yy );
              // pw5.format(" style=\"fill:none;stroke:%s;stroke-width:0.1\" >\n", color_str );
              // Log.v("DistoX", "Section point <" + point.mOptions + "> " + point.cx + " " + point.cy );
              // option: -scrap survey-xx#
              String scrapfile = point.mOptions.substring( 7 ) + ".tdr";
              // TODO open file survey-xx#.tdr and convert it to svg
              tdrToSvg( pw5, scrapfile, xx, yy, -DrawingUtil.CENTER_X, -DrawingUtil.CENTER_Y );
              // pw5.format("</g>\n");
            } else {
              toSvg( pw5, point, color_str, xoff, yoff );
            }
          }
          out.write( sw5.getBuffer().toString() );
          out.flush();
        }
        // stations
        StringWriter sw6 = new StringWriter();
        PrintWriter pw6  = new PrintWriter(sw6);
        if ( TDSetting.mAutoStations ) {
          for ( DrawingStationName name : plot.getStations() ) { // auto-stations
            toSvg( pw6, name, xoff, yoff );
          }
          out.write( sw6.getBuffer().toString() );
          out.flush();
        } else {
          for ( DrawingStationPath st_path : plot.getUserStations() ) { // user-chosen
            toSvg( pw6, st_path, xoff, yoff );
          }
          out.write( sw6.getBuffer().toString() );
          out.flush();
        }
      }
      out.write("</g>\n");
      out.write("</svg>\n");

      if ( TDSetting.mSvgInHtml ) { // SVG_IN_HTML
        out.write("</body>\n</html>\n");
      }

      out.flush();
    } catch ( IOException e ) {
      // FIXME
      TDLog.Error( "SVG io-exception " + e.getMessage() );
    }
  }

  static private void toSvg( PrintWriter pw, DrawingStationName name, float xoff, float yoff )
  {
    pw.format("<text font-size=\"20\" font=\"sans-serif\" fill=\"violet\" stroke=\"none\" text-amchor=\"middle\"");
    pw.format(Locale.US, " x=\"%.2f\" y=\"%.2f\">", xoff + name.cx, yoff + name.cy );
    pw.format("%s</text>\n", name.name() );
  }

  static private void toSvg( PrintWriter pw, DrawingStationPath st, float xoff, float yoff )
  {
    pw.format("<text font-size=\"20\" font=\"sans-serif\" fill=\"black\" stroke=\"none\" text-amchor=\"middle\"");
    pw.format(Locale.US, " x=\"%.2f\" y=\"%.2f\">", xoff + st.cx, yoff + st.cy );
    pw.format("%s</text>\n", st.name() );
  }

  static private void toSvg( PrintWriter pw, DrawingLinePath line, String color, float xoff, float yoff ) 
  {
    String th_name = BrushManager.mLineLib.getSymbolThName( line.mLineType ); 
    pw.format("  <path stroke=\"%s\" stroke-width=\"2\" fill=\"none\" class=\"%s\"", color, th_name );
    if ( th_name.equals( "arrow" ) ) pw.format(" marker-end=\"url(#Triangle)\"");
    else if ( th_name.equals( "section" ) ) pw.format(" stroke-dasharray=\"5 3 \"");
    else if ( th_name.equals( "fault" ) ) pw.format(" stroke-dasharray=\"8 4 \"");
    else if ( th_name.equals( "floor-meander" ) ) pw.format(" stroke-dasharray=\"6 2 \"");
    else if ( th_name.equals( "ceiling-meander" ) ) pw.format(" stroke-dasharray=\"6 2 \"");
    pw.format(" d=\"");
    LinePoint p = line.mFirst;
    pw.format(Locale.US, "M %.2f %.2f", xoff+p.x, yoff+p.y );
    for ( p = p.mNext; p != null; p = p.mNext ) { 
      pw.format(Locale.US, " L %.2f %.2f", xoff+p.x, yoff+p.y );
    }
    pw.format("\" />\n");
  }

  static private void toSvg( PrintWriter pw, DrawingAreaPath area, String color, float xoff, float yoff )
  {
    pw.format("  <path stroke=\"black\" stroke-width=\"1\" fill=\"%s\" fill-opacity=\"0.5\" d=\"", color );
    LinePoint p = area.mFirst;
    pw.format(Locale.US, "M %.2f %.2f", xoff+p.x, yoff+p.y );
    for ( p = p.mNext; p != null; p = p.mNext ) { 
      pw.format(Locale.US, " L %.2f %.2f", xoff+p.x, yoff+p.y );
    }
    pw.format(" Z\" />\n");
  }

  static private void toSvg( PrintWriter pw, DrawingPointPath point, String color, float xoff, float yoff )
  {
    // FIXME point scale factor is 0.3
    int idx = point.mPointType;
    String name = BrushManager.mPointLib.getSymbolThName( idx );
    pw.format("<!-- point %s -->\n", name );
    if ( name.equals("label") ) {
      DrawingLabelPath label = (DrawingLabelPath)point;
      pw.format(Locale.US, "<text x=\"%.2f\" y=\"%.2f\" ", xoff+point.cx, yoff+point.cy );
      pw.format(" style=\"fill:black;stroke:black;stroke-width:0.3\">%s</text>\n", label.mPointText );
    // } else if ( name.equals("continuation") ) {
    //   pw.format(Locale.US, "<text x=\"%.2f\" y=\"%.2f\" ", xoff+point.cx, yoff+point.cy );
    //   pw.format(" style=\"fill:none;stroke:black;stroke-width:0.3\">\?</text>\n" );
    // } else if ( name.equals("danger") ) {
    //   pw.format(Locale.US, "<text x=\"%.2f\" y=\"%.2f\" ", xoff+point.cx, yoff+point.cy );
    //   pw.format(" style=\"fill:none;stroke:red;stroke-width:0.3\">!</text>\n" );
    } else if ( idx == BrushManager.mPointLib.mPointSectionIndex ) {
      /* nothing */
    } else {
      SymbolPoint sp = (SymbolPoint)BrushManager.mPointLib.getSymbolByIndex( idx );
      if ( sp != null ) {
        pw.format(Locale.US, "<g transform=\"translate(%.2f,%.2f),scale(10),rotate(%.2f)\" \n", 
          xoff+point.cx, yoff+point.cy, point.mOrientation );
        pw.format(" style=\"fill:none;stroke:%s;stroke-width:0.1\" >\n", color );
        pw.format("%s\n", sp.mSvg );
        pw.format("</g>\n");
      } else {
        pw.format(Locale.US, "<circle cx=\"%.2f\" cy=\"%.2f\" r=\"10\" ", xoff+point.cx, yoff+point.cy );
        pw.format(" style=\"fill:none;stroke:black;stroke-width:0.1\" />\n");
      }
    }
  }

  static private void tdrToSvg( PrintWriter pw, String scrapfile, float dx, float dy, float xoff, float yoff )
  {
    try {
      FileInputStream fis = new FileInputStream( TDPath.getTdrFile( scrapfile ) );
      BufferedInputStream bfis = new BufferedInputStream( fis );
      DataInputStream dis = new DataInputStream( bfis );
      int version = DrawingIO.skipTdrHeader( dis );
      // Log.v("DistoX", "tdr to svg delta " + dx + " " + dy + " Offset " + xoff + " " + yoff );

      DrawingPath path = null;
      boolean done = false;
      while ( ! done ) {
        int what = dis.read();
        switch ( what ) {
          case 'P':
            path = DrawingPointPath.loadDataStream( version, dis, dx, dy, null );
            toSvg( pw, (DrawingPointPath)path, pathToColor(path), xoff, yoff );
            break;
          case 'T':
            path = DrawingLabelPath.loadDataStream( version, dis, dx, dy );
            toSvg( pw, (DrawingLabelPath)path, pathToColor(path), xoff, yoff );
            break;
          case 'L':
            path = DrawingLinePath.loadDataStream( version, dis, dx, dy, null );
            toSvg( pw, (DrawingLinePath)path, pathToColor(path), xoff, yoff );
            break;
          case 'A':
            path = DrawingAreaPath.loadDataStream( version, dis, dx, dy, null );
            toSvg( pw, (DrawingAreaPath)path, pathToColor(path), xoff, yoff );
            break;
          case 'U':
            path = DrawingStationPath.loadDataStream( version, dis ); // consume DrawingStationName data
            break;
          case 'X':
            path = DrawingStationName.loadDataStream( version, dis ); // consume DrawingStationName data
            break;
          case 'F':
            done = true;
            break;
        }
      }
    } catch ( FileNotFoundException e ) { // this is OK
    } catch ( IOException e ) {
      e.printStackTrace();
    }
  }

}

