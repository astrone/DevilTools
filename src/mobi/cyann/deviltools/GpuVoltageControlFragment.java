/**
 * GpuVoltageControlFragment.java
 * Nov 6, 2011 7:27:58 PM
 */
package mobi.cyann.deviltools;

import java.util.ArrayList;
import java.util.List;

import mobi.cyann.deviltools.preference.IntegerPreference;
import mobi.cyann.deviltools.preference.VoltagePreference;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceCategory;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.util.Log;
import android.view.View;
import android.widget.CheckBox;

/**
 * @author arif
 *
 */
public class GpuVoltageControlFragment extends BasePreferenceFragment implements OnPreferenceChangeListener {
	private final static String LOG_TAG = "DevilTools.GpuVoltageControlActivity";
	
	private IntegerPreference maxGpuVolt;
	
	private List<Integer> gpuVoltages;
	
	private SharedPreferences preferences;
	
	public GpuVoltageControlFragment() {
		super(R.layout.gpu_voltage);
		
		gpuVoltages = new ArrayList<Integer>();
	}

	@Override
	public void onPreferenceAttached(PreferenceScreen rootPreference, int xmlId) {
		preferences = PreferenceManager.getDefaultSharedPreferences(getActivity());
		
		maxGpuVolt = (IntegerPreference)findPreference(getString(R.string.key_max_gpu_volt));
		findPreference(getString(R.string.key_default_voltage)).setOnPreferenceChangeListener(this);

		gpuVoltages.clear();

		if(maxGpuVolt.getValue() == -1) {
			Log.d(LOG_TAG, "read from mali_table");
			// if we can't get customvoltage mod, then try to read UV_mV_table
			readUvmvTable();
		}else {
			Log.d(LOG_TAG, "read from customvoltage");
			readVoltages(maxGpuVolt, getString(R.string.key_gpu_volt_pref), "armvolt_", "/sys/class/misc/customvoltage/arm_volt", gpuVoltages);
		}
		
		super.onPreferenceAttached(rootPreference, xmlId);
	}
	
	private void showWarningDialog() {
		if(!preferences.getBoolean(getString(R.string.key_dont_show_volt_warning), false)) {
			final View content = getActivity().getLayoutInflater().inflate(R.layout.warning_dialog, null);
			AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
			builder.setView(content);
			builder.setTitle(getString(R.string.label_warning));
			builder.setPositiveButton(getString(R.string.label_ok), new OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					boolean val = ((CheckBox)content.findViewById(R.id.checkboxWarning)).isChecked();
					Editor ed = preferences.edit();
					ed.putBoolean(getString(R.string.key_dont_show_volt_warning), val);
					ed.commit();
					dialog.dismiss();
				}
			});
			builder.show();
		}
	}

	private void createDefaultVoltPreference(PreferenceCategory parent, String voltPrefix, int i, String title, int value) {
		VoltagePreference vp = new VoltagePreference(getActivity());
		vp.setKey(voltPrefix + i);
		vp.setTitle(title);
		vp.setValue(value);
		vp.setSummary("0");
		vp.setMaxValue(1500);
		vp.setMinValue(750);
		vp.setStep(25);
		vp.setMetrics("mV");
		vp.setPersistent(false);
		vp.setIgnoreInterface(true);
		vp.setOnPreferenceChangeListener(this);
		
		parent.addPreference(vp);
		
		vp.setDependency(getString(R.string.key_default_voltage));
	}
	
	private void readUvmvTable() {
		PreferenceCategory c = (PreferenceCategory)findPreference(getString(R.string.key_gpu_volt_pref));
		SysCommand sc = SysCommand.getInstance();
		int count = sc.readSysfs("/sys/class/misc/mali_control/voltage_control");
		for(int i = 0; i < count; ++i) {
			String line = sc.getLastResult(i);
			String parts[] = line.split(":");
			if(parts.length >= 2) {
				int volt = Integer.parseInt(parts[1].substring(1, parts[1].length()-3));
	
				Log.d(LOG_TAG, line);
				createDefaultVoltPreference(c, "uvmvtable_", i, parts[0], volt);
				
				gpuVoltages.add(volt);
			}
		}
		if(gpuVoltages.size() > 0) {
			saveVoltages(getString(R.string.key_uvmvtable_pref), gpuVoltages, null);
		}
	}
	
	private void readVoltages(IntegerPreference maxVolt, String catKey, String voltPrefix, String voltDevice, List<Integer>voltList) {
		PreferenceCategory c = (PreferenceCategory)findPreference(catKey);
		if(c != null) {
			while(c.getPreferenceCount() > 1) { // clear all preference except the first one
				Preference tp = c.getPreference(1);
				c.removePreference(tp);
			}
			
			SysCommand sc = SysCommand.getInstance();
			int count = sc.readSysfs(voltDevice);
			for(int i = 0; i < count; ++i) {
				String line = sc.getLastResult(i);
				String parts[] = line.split(":");
				int volt = Integer.parseInt(parts[1].substring(1, parts[1].length()-3));
	
				Log.d(LOG_TAG, line);
				
				createDefaultVoltPreference(c, voltPrefix, i, parts[0], volt);
				
				voltList.add(volt);
			}
			saveVoltages(catKey, voltList, null);
		}
	}

	private void saveVoltages(String key, List<Integer> voltageList, String deviceString) {
		StringBuilder s = new StringBuilder();
		for(int i = 0; i < voltageList.size(); ++i) {
			s.append(voltageList.get(i));
			s.append(" ");
		}
		Log.d(LOG_TAG, "voltages:" + s.toString());
		if(deviceString != null) {
			SysCommand.getInstance().writeSysfs(deviceString, s.toString());
		}
		// save to xml pref
		Editor ed = preferences.edit();
		ed.putString(key, s.toString());
		ed.commit();
	}

	@Override
	public boolean onPreferenceChange(Preference preference, Object newValue) {
		if(preference.getKey().startsWith("gpuvolt_")) {
			String parts[] = preference.getKey().split("_");
			int i = Integer.parseInt(parts[1]);
			gpuVoltages.set(i, (Integer)newValue);
			saveVoltages(getString(R.string.key_gpu_volt_pref), gpuVoltages, "/sys/class/misc/customvoltage/gpu_volt");
		}else if(preference.getKey().startsWith("uvmvtable_")) {
			String parts[] = preference.getKey().split("_");
			int i = Integer.parseInt(parts[1]);
			gpuVoltages.set(i, (Integer)newValue);
			saveVoltages(getString(R.string.key_uvmvtable_pref), gpuVoltages, "/sys/class/misc/mali_control/voltage_control");
		}else if(preference.getKey().equals(getString(R.string.key_default_voltage))) {
			if(!(Boolean)newValue) {
				showWarningDialog();
			}
		}
		return true;
	}
}
