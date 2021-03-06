/** @file DeviceA3InfoDialog.java
 *
 * @author marco corvi
 * @date nov 2011
 *
 * @brief TopoDroid DistoX A3 device info dialog
 * --------------------------------------------------------
 *  Copyright This sowftare is distributed under GPL-3.0 or later
 *  See the file COPYING.
 * --------------------------------------------------------
 */
package com.topodroid.DistoX;

import android.os.Bundle;
import android.app.Dialog;
// import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.DialogInterface;
// import android.content.DialogInterface.OnCancelListener;
// import android.content.DialogInterface.OnDismissListener;

import android.view.View;
import android.view.ViewGroup.LayoutParams;

import android.widget.TextView;
import android.widget.RadioButton;
import android.widget.Button;

class DeviceA3InfoDialog extends MyDialog
                         implements View.OnClickListener
{
  private RadioButton mRBa3;
  private RadioButton mRBx310;
  private Button   mBTok;
  // private Button   mBTcancel;

  DeviceActivity mParent;
  Device mDevice;

  TextView tv_serial;
  TextView tv_statusAngle;
  TextView tv_statusCompass;
  TextView tv_statusCalib;
  TextView tv_statusSilent;

  DeviceA3InfoDialog( Context context, DeviceActivity parent, Device device )
  {
    super( context, R.string.DeviceA3InfoDialog );
    mParent = parent;
    mDevice = device;
  }


  @Override
  public void onCreate( Bundle bundle )
  {
    super.onCreate( bundle );

    setContentView( R.layout.device_a3_info_dialog );
    getWindow().setLayout( LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT );

    mRBa3   = (RadioButton) findViewById( R.id.rb_a3 );
    mRBx310 = (RadioButton) findViewById( R.id.rb_x310 );
    mRBa3.setChecked( true );
    // mRBx310.setChecked( false );

    TextView tv_address       = (TextView) findViewById( R.id.tv_address );
    tv_serial        = (TextView) findViewById( R.id.tv_serial );
    tv_statusAngle   = (TextView) findViewById( R.id.tv_status_angle );
    tv_statusCompass = (TextView) findViewById( R.id.tv_status_compass );
    tv_statusCalib   = (TextView) findViewById( R.id.tv_status_calib );
    tv_statusSilent  = (TextView) findViewById( R.id.tv_status_silent );

    setTitle( mParent.getResources().getString( R.string.device_info ) );

    tv_address.setText( String.format( mParent.getResources().getString( R.string.device_address ), mDevice.mAddress ) );

    // tv_serial.setText( "" );
    // tv_statusAngle.setText(   "" );
    // tv_statusCompass.setText( "" );
    // tv_statusCalib.setText(   "" );
    // tv_statusSilent.setText(  "" );
    mParent.readA3Info( this );

    mBTok = (Button) findViewById( R.id.btn_ok );
    mBTok.setOnClickListener( this );
    // mBTcancel = (Button) findViewById( R.id.button_cancel );
    // mBTcancel.setOnClickListener( this );
  }

  public void updateInfo( DeviceA3Info info ) 
  {
    if ( info == null ) return;
    tv_serial.setText( info.mCode );
    tv_statusAngle.setText(   info.mAngle   );
    tv_statusCompass.setText( info.mCompass );
    tv_statusCalib.setText(   info.mCalib   );
    tv_statusSilent.setText(  info.mSilent  );
  }

  @Override
  public void onClick(View view)
  {
    Button b = (Button)view;
    if ( b == mBTok ) {
      // TODO ask confirm
      TopoDroidAlertDialog.makeAlert( mParent, mParent.getResources(),
                                mParent.getResources().getString( R.string.device_model_set ) + " ?",
        new DialogInterface.OnClickListener() {
          @Override
          public void onClick( DialogInterface dialog, int btn ) {
            doSetModel( );
          }
        }
      );
    // } else if ( b == mBTcancel ) {
    //   dismiss();
    }
  }

  void doSetModel()
  {
    if ( mRBa3.isChecked() ) {
      mParent.setDeviceModel( mDevice, Device.DISTO_A3 );
    } else if ( mRBx310.isChecked() ) {
      mParent.setDeviceModel( mDevice, Device.DISTO_X310 );
    }
  }

}
