/** @file HullSide.java
 *
 * @author marco corvi
 * @date june 2017
 *
 * @brief TopoDroid Delaunay: side of the "convex" hull
 * --------------------------------------------------------
 *  Copyright This sowftare is distributed under GPL-3.0 or later
 *  See the file COPYING.
 * --------------------------------------------------------
 */
package com.topodroid.DistoX;


class HullSide
{
  DLNSide side;
  HullSide next;
  HullSide prev;

  HullSide( DLNSide s ) 
  { 
    side = s;
    next = null;
    prev = null;
  }
}
