package org.myrobotlab.control;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.myrobotlab.logging.LoggerFactory;
import org.myrobotlab.service.GUIService;
import org.myrobotlab.service.InMoovGestureCreator.ServoItemHolder;
import org.myrobotlab.service._TemplateService;
import org.slf4j.Logger;

/**
 * based on _TemplateServiceGUI
 */
/**
 *
 * @author LunDev (github), Ma. Vo. (MyRobotlab)
 */
public class InMoovGestureCreatorGUI extends ServiceGUI implements
		ActionListener, ItemListener {

	static final long serialVersionUID = 1L;
	public final static Logger log = LoggerFactory
			.getLogger(InMoovGestureCreatorGUI.class.getCanonicalName());

	boolean[] tabs_main_checkbox_states;

	JTextArea generatedcode;
	JList framelist;
	JTextField frame_add_textfield;
	JTextField frame_addsleep_textfield;
	JTextField frame_addspeech_textfield;

	JButton exportcode;
	JButton testgesture;

	JButton frame_connect;
	JButton frame_add;
	JButton frame_addspeed;
	JButton frame_addsleep;
	JButton frame_addspeech;

	JButton frame_importminresmax;
	JButton frame_remove;
	JButton frame_load;
	JButton frame_update;
	JButton frame_copy;
	JButton frame_up;
	JButton frame_down;
	JButton frame_test;
	JCheckBox frame_moverealtime;

	public InMoovGestureCreatorGUI(final String boundServiceName,
			final GUIService myService, final JTabbedPane tabs) {
		super(boundServiceName, myService, tabs);
	}

	public void init() {

		// display:
		// |--------------------|
		// |####################|
		// |########top#########|
		// |####################|
		// |--------------------| <- splitpanetopbottom
		// |######bottom########|
		// |####################|
		// |--------------------|

		// bottom:
		// |--------------------|
		// |bott#|##bottom2#####|
		// |#om1#|##############|
		// |--------------------|
		// ######/\
		// splitpanebottom1bottom2

		// bottom1:
		// |----------|
		// |bottom1top| <- JButton's: exportcode, testgesture
		// |----------|
		// |##########| <- JTextArea: generatedcode
		// |##########|
		// |----------|

		// bottom2:
		// |----------|
		// |bottom2top| <- JButton's & JTextField's: [frame_] connect, add,
		// addsleep, addspeech
		// |##########| <- JButton's: [frame_] importminresmax, remove, load,
		// update, copy, up, down, test
		// |----------|
		// |##########|
		// |##########| <- JList: framelist
		// |##########|
		// |----------|

		// predefined min- / res- / max- positions
		int[][][] minresmaxpos = {
				{ { 0, 90, 180 }, { 0, 90, 180 }, { 0, 90, 180 },
						{ 0, 90, 180 }, { 0, 90, 180 }, { 0, 90, 180 } },
				{ { 0, 90, 180 }, { 0, 90, 180 }, { 0, 90, 180 },
						{ 0, 90, 180 } },
				{ { 0, 90, 180 }, { 0, 90, 180 }, { 0, 90, 180 },
						{ 0, 90, 180 }, { 0, 90, 180 }, { 0, 90, 180 } },
				{ { 0, 90, 180 }, { 0, 90, 180 }, { 0, 90, 180 },
						{ 0, 90, 180 } },
				{ { 0, 90, 180 }, { 0, 90, 180 }, { 0, 90, 180 },
						{ 0, 90, 180 }, { 0, 90, 180 } },
				{ { 0, 90, 180 }, { 0, 90, 180 }, { 0, 90, 180 } } };

		JPanel top = new JPanel();

		JTabbedPane top_tabs = new JTabbedPane(JTabbedPane.TOP,
				JTabbedPane.WRAP_TAB_LAYOUT);

		// JPanels for the JTabbedPane
		final JPanel mainpanel = new JPanel();
		final JPanel c1panel = new JPanel();
		final JPanel c2panel = new JPanel();
		final JPanel c3panel = new JPanel();

		// mainpanel (enabeling / disabeling sections)
		mainpanel.setLayout(new BoxLayout(mainpanel, BoxLayout.Y_AXIS));
		tabs_main_checkbox_states = new boolean[6];
		for (int i = 0; i < 6; i++) {
			String name = "";
			if (i == 0) {
				name = "Right Hand";
			} else if (i == 1) {
				name = "Right Arm";
			} else if (i == 2) {
				name = "Left Hand";
			} else if (i == 3) {
				name = "Left Arm";
			} else if (i == 4) {
				name = "Head";
			} else if (i == 5) {
				name = "Torso";
			}

			final int fi = i;

			final JCheckBox checkbox = new JCheckBox(name);
			checkbox.addItemListener(new ItemListener() {
				@Override
				public void itemStateChanged(ItemEvent arg0) {
					tabs_main_checkbox_states[fi] = checkbox.isSelected();
					myService.send(boundServiceName,
							"tabs_main_checkbox_states_changed",
							tabs_main_checkbox_states);
				}

			});
			checkbox.setSelected(true);
			mainpanel.add(checkbox);
		}

		Container c1con = c1panel;
		Container c2con = c2panel;
		Container c3con = c3panel;

		GridBagLayout c1gbl = new GridBagLayout();
		c1con.setLayout(c1gbl);
		GridBagLayout c2gbl = new GridBagLayout();
		c2con.setLayout(c2gbl);
		GridBagLayout c3gbl = new GridBagLayout();
		c3con.setLayout(c3gbl);

		// c1-, c2-, c3-panel
		for (int i1 = 0; i1 < 6; i1++) {
			
			Container con = null;
			GridBagLayout gbl = null;
			
			if (i1 == 0 || i1 == 1) {
				con = c1con;
				gbl = c1gbl;
			} else if (i1 == 2 || i1 == 3) {
				con = c2con;
				gbl = c2gbl;
			} else if (i1 == 4 || i1 == 5) {
				con = c3con;
				gbl = c3gbl;
			}
			
			int size = 0;

			if (i1 == 0 || i1 == 2) {
				size = 6;
			} else if (i1 == 1 || i1 == 3) {
				size = 4;
			} else if (i1 == 4) {
				size = 5;
			} else if (i1 == 5) {
				size = 3;
			}
			
			int offset = 0;
			if (i1 == 1 || i1 == 3) {
				offset = 6;
			} else if (i1 == 5) {
				offset = 5;
			}

			ServoItemHolder[] sih1 = new ServoItemHolder[size];

			for (int i2 = 0; i2 < size; i2++) {
				ServoItemHolder sih11 = new ServoItemHolder();

				String servoname = "";

				if (i1 == 0 || i1 == 2) {
					if (i2 == 0) {
						servoname = "thumb";
					} else if (i2 == 1) {
						servoname = "index";
					} else if (i2 == 2) {
						servoname = "majeure";
					} else if (i2 == 3) {
						servoname = "ringfinger";
					} else if (i2 == 4) {
						servoname = "pinky";
					} else if (i2 == 5) {
						servoname = "wrist";
					}
				} else if (i1 == 1 || i1 == 3) {
					if (i2 == 0) {
						servoname = "bicep";
					} else if (i2 == 1) {
						servoname = "rotate";
					} else if (i2 == 2) {
						servoname = "shoulder";
					} else if (i2 == 3) {
						servoname = "omoplate";
					}
				} else if (i1 == 4) {
					if (i2 == 0) {
						servoname = "neck";
					} else if (i2 == 1) {
						servoname = "rothead";
					} else if (i2 == 2) {
						servoname = "eyeX";
					} else if (i2 == 3) {
						servoname = "eyeY";
					} else if (i2 == 4) {
						servoname = "jaw";
					}
				} else if (i1 == 5) {
					if (i2 == 0) {
						servoname = "topStom";
					} else if (i2 == 1) {
						servoname = "midStom";
					} else if (i2 == 2) {
						servoname = "lowStom";
					}
				}

				sih11.fin = new JLabel(servoname);
				sih11.min = new JLabel(minresmaxpos[i1][i2][0] + "");
				sih11.res = new JLabel(minresmaxpos[i1][i2][1] + "");
				sih11.max = new JLabel(minresmaxpos[i1][i2][2] + "");
				sih11.sli = new JSlider();
				customizeslider(sih11.sli, i1, i2, minresmaxpos[i1][i2]);
				sih11.akt = new JLabel(sih11.sli.getValue() + "");
				sih11.spe = new JTextField("1.00");

				// x y w h wx wy
				gridbaglayout_addComponent(con, gbl, sih11.fin, offset+i2, 0, 1, 1,
						1.0, 1.0);
				gridbaglayout_addComponent(con, gbl, sih11.min, offset+i2, 1, 1, 1,
						1.0, 1.0);
				gridbaglayout_addComponent(con, gbl, sih11.res, offset+i2, 2, 1, 1,
						1.0, 1.0);
				gridbaglayout_addComponent(con, gbl, sih11.max, offset+i2, 3, 1, 1,
						1.0, 1.0);
				gridbaglayout_addComponent(con, gbl, sih11.sli, offset+i2, 4, 1, 1,
						1.0, 1.0);
				gridbaglayout_addComponent(con, gbl, sih11.akt, offset+i2, 5, 1, 1,
						1.0, 1.0);
				gridbaglayout_addComponent(con, gbl, sih11.spe, offset+i2, 6, 1, 1,
						1.0, 1.0);

				sih1[i2] = sih11;
			}
			myService.send(boundServiceName, "servoitemholder_set_sih1", i1,
					sih1);
		}
		
		top_tabs.addTab("Main", mainpanel);
		top_tabs.addTab("Right Side", c1panel);
		top_tabs.addTab("Left Side", c2panel);
		top_tabs.addTab("Head + Torso", c3panel);
		
		top.add(BorderLayout.CENTER, top_tabs);

		JPanel bottom = new JPanel();

		JPanel bottom1 = new JPanel();

		JPanel bottom1top = new JPanel();

		exportcode = new JButton("Export Code");
		bottom1top.add(exportcode);
		exportcode.addActionListener(this);

		testgesture = new JButton("Test Gesture");
		bottom1top.add(testgesture);
		testgesture.addActionListener(this);

		bottom1.add(BorderLayout.NORTH, bottom1top);

		generatedcode = new JTextArea(10, 20);
		generatedcode.setLineWrap(true);
		generatedcode.setEditable(false);

		JScrollPane generatedcodescroller = new JScrollPane(generatedcode);
		generatedcodescroller
				.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
		generatedcodescroller
				.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_ALWAYS);

		bottom1.add(BorderLayout.CENTER, generatedcodescroller);

		JPanel bottom2 = new JPanel();
		bottom2.setLayout(new BoxLayout(bottom2, BoxLayout.Y_AXIS));

		JPanel bottom2top = new JPanel();
		bottom2top.setLayout(new BoxLayout(bottom2top, BoxLayout.Y_AXIS));

		JPanel bottom2top1 = new JPanel();
		bottom2top1.setLayout(new BoxLayout(bottom2top1, BoxLayout.X_AXIS));

		frame_connect = new JButton("Connect");
		bottom2top1.add(frame_connect);
		frame_connect.addActionListener(this);

		frame_add_textfield = new JTextField("Frame-Name");
		bottom2top1.add(frame_add_textfield);

		frame_add = new JButton("Add");
		bottom2top1.add(frame_add);
		frame_add.addActionListener(this);

		frame_addspeed = new JButton("Add Speed");
		bottom2top1.add(frame_addspeed);
		frame_addspeed.addActionListener(this);

		frame_addsleep_textfield = new JTextField("Seconds of Sleep");
		bottom2top1.add(frame_addsleep_textfield);

		frame_addsleep = new JButton("Add Sleep");
		bottom2top1.add(frame_addsleep);
		frame_addsleep.addActionListener(this);

		frame_addspeech_textfield = new JTextField("Speech");
		bottom2top1.add(frame_addspeech_textfield);

		frame_addspeech = new JButton("Add Speech");
		bottom2top1.add(frame_addspeech);
		frame_addspeech.addActionListener(this);

		bottom2top.add(bottom2top1);

		JPanel bottom2top2 = new JPanel();
		bottom2top2.setLayout(new BoxLayout(bottom2top2, BoxLayout.X_AXIS));

		frame_importminresmax = new JButton("Import Min Rest Max");
		bottom2top2.add(frame_importminresmax);
		frame_importminresmax.addActionListener(this);

		frame_remove = new JButton("Remove");
		bottom2top2.add(frame_remove);
		frame_remove.addActionListener(this);

		frame_load = new JButton("Load");
		bottom2top2.add(frame_load);
		frame_load.addActionListener(this);

		frame_update = new JButton("Update");
		bottom2top2.add(frame_update);
		frame_update.addActionListener(this);

		frame_copy = new JButton("Copy");
		bottom2top2.add(frame_copy);
		frame_copy.addActionListener(this);

		frame_up = new JButton("Up");
		bottom2top2.add(frame_up);
		frame_up.addActionListener(this);

		frame_down = new JButton("Down");
		bottom2top2.add(frame_down);
		frame_down.addActionListener(this);

		frame_test = new JButton("Test");
		bottom2top2.add(frame_test);
		frame_test.addActionListener(this);

		frame_moverealtime = new JCheckBox("Move Real Time");
		frame_moverealtime.setSelected(false);
		bottom2top2.add(frame_moverealtime);
		frame_moverealtime.addItemListener(this);

		bottom2top.add(bottom2top2);

		bottom2.add(BorderLayout.NORTH, bottom2top);

		String[] te = {
				"                                                                                                                                                                                                        ",
				"T1", "T2", "T3", "T4", "T5", "T6", "T7", "T8", "T9", "T10" };

		framelist = new JList(te);
		framelist.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

		JScrollPane framelistscroller = new JScrollPane(framelist);
		framelistscroller
				.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
		framelistscroller
				.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

		bottom2.add(BorderLayout.CENTER, framelistscroller);

		JSplitPane splitpanebottom1bottom2 = new JSplitPane(
				JSplitPane.HORIZONTAL_SPLIT, bottom1, bottom2);
		splitpanebottom1bottom2.setOneTouchExpandable(true);
		// splitpanebottom1bottom2.setDividerLocation(200);

		bottom.add(splitpanebottom1bottom2);

		JSplitPane splitpanetopbottom = new JSplitPane(
				JSplitPane.VERTICAL_SPLIT, top, bottom);
		splitpanetopbottom.setOneTouchExpandable(true);
		// splitpanetopbottom.setDividerLocation(300);

		display.add(splitpanetopbottom);
	}

	public void getState(_TemplateService template) {
		// I think I should do something with this ...
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {

			}
		});
	}

	@Override
	public void attachGUI() {
		// commented out subscription due to this class being used for
		// un-defined gui's

		// subscribe("publishState", "getState", _TemplateService.class);
		// send("publishState");
	}

	@Override
	public void detachGUI() {
		// commented out subscription due to this class being used for
		// un-defined gui's

		// unsubscribe("publishState", "getState", _TemplateService.class);
	}

	@Override
	public void actionPerformed(ActionEvent ae) {
		Object o = ae.getSource();

		// Button - Events
		if (o == testgesture) {
			myService.send(boundServiceName, "testgesture");
		} else if (o == exportcode) {
			myService.send(boundServiceName, "exportcode", generatedcode);
		} else if (o == frame_connect) {
			myService.send(boundServiceName, "frame_connect", frame_connect);
		} else if (o == frame_add) {
			myService.send(boundServiceName, "frame_add", framelist,
					frame_add_textfield);
		} else if (o == frame_addspeed) {
			myService.send(boundServiceName, "frame_addspeed", framelist);
		} else if (o == frame_addsleep) {
			myService.send(boundServiceName, "frame_addsleep", framelist,
					frame_addsleep_textfield);
		} else if (o == frame_addspeech) {
			myService.send(boundServiceName, "frame_addspeech", framelist,
					frame_addspeech_textfield);
		} else if (o == frame_importminresmax) {
			myService.send(boundServiceName, "frame_importminresmax");
		} else if (o == frame_remove) {
			myService.send(boundServiceName, "frame_remove", framelist);
		} else if (o == frame_load) {
			myService.send(boundServiceName, "frame_load", framelist,
					frame_add_textfield, frame_addsleep_textfield,
					frame_addspeech_textfield);
		} else if (o == frame_update) {
			myService.send(boundServiceName, "frame_update", framelist,
					frame_add_textfield, frame_addsleep_textfield,
					frame_addspeech_textfield);
		} else if (o == frame_copy) {
			myService.send(boundServiceName, "frame_copy", framelist);
		} else if (o == frame_up) {
			myService.send(boundServiceName, "frame_up", framelist);
		} else if (o == frame_down) {
			myService.send(boundServiceName, "frame_down", framelist);
		} else if (o == frame_test) {
			myService.send(boundServiceName, "frame_test", framelist);
		}
		myService.send(boundServiceName, "publishState");
	}

	@Override
	public void itemStateChanged(ItemEvent ie) {
		Object o = ie.getSource();

		// CheckBox - Events
		if (o == frame_moverealtime) {
			myService.send(boundServiceName, "frame_moverealtime",
					frame_moverealtime);
		}
	}

	public void gridbaglayout_addComponent(Container cont, GridBagLayout gbl,
			Component c, int x, int y, int width, int height, double weightx,
			double weighty) {
		// function for easier gridbaglayout's
		GridBagConstraints gbc = new GridBagConstraints();
		gbc.fill = GridBagConstraints.BOTH;
		gbc.gridx = x;
		gbc.gridy = y;
		gbc.gridwidth = width;
		gbc.gridheight = height;
		gbc.weightx = weightx;
		gbc.weighty = weighty;
		gbl.setConstraints(c, gbc);
		cont.add(c);
	}

	public void customizeslider(JSlider slider, final int t1, final int t2,
			int[] minresmaxpos11) {
		// preset the slider
		slider.setOrientation(SwingConstants.VERTICAL);
		slider.setMinimum(minresmaxpos11[0]);
		slider.setMaximum(minresmaxpos11[2]);
		slider.setMajorTickSpacing(20);
		slider.setMinorTickSpacing(1);
		slider.createStandardLabels(1);
		slider.setPaintTicks(true);
		slider.setPaintLabels(true);
		slider.setValue((minresmaxpos11[0] + minresmaxpos11[2]) / 2);

		slider.addChangeListener(new ChangeListener() {

			@Override
			public void stateChanged(ChangeEvent ce) {
				myService.send(boundServiceName,
						"servoitemholder_slider_changed", t1, t2);
			}
		});
	}
}
