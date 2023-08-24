import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Connection;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Collections;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

/*
 * This multi-threaded web crawler supports single crawl action with multiple displays
 * 
 * Each crawl generates a new database which the display button interacts with. The DB is named after the current time.
 * Once crawl button is clicked again, a new DB is then generated and the display button will be associated with the new DB.
 * 
 * One possible and helpful improvement would let users pick which DB to display.
 * */


public class Crawler extends JFrame{

	private static final long serialVersionUID = 1L;
	private static final int WIDTH = 500;
	private static final int HEIGHT = 700;
	
	private JPanel main_panel;
	
	private JPanel search_panel;
	private JPanel search_top_panel;
	private JPanel search_bot_panel;
	private JLabel starting_lbl;
	private JTextField init_link;
	private JLabel depth_lbl;
	private JTextField init_depth;
	private JButton begin_button;
	
	private JPanel record_panel;
	private JTextArea record_area;
	private JScrollPane sp;
	
	private JPanel result_panel;
	private JButton disp_button;
	private JTextField idx;
	private JLabel disp_lbl;
	private JTextArea result_set;
	private JScrollPane sp2;
	private JPanel option_panel;

	private int depth;
	private List<String> visitedLinks = new ArrayList<>();
	private List<String> sync_vL;
	
	private Connection conn;
	
	private Statement statement;
	private String filename;
	
	Crawler(){
		super("Multi-Threaded Web Crawler");
	
		sync_vL = Collections.synchronizedList(visitedLinks);
		
		main_panel = new JPanel();
		search_panel = new JPanel();
		search_top_panel = new JPanel();
		search_bot_panel = new JPanel();
		record_panel = new JPanel();
		init_link = new JTextField();
		starting_lbl = new JLabel("Starting webpage:");
		depth_lbl = new JLabel("Depth:");
		init_depth = new JTextField();
		begin_button = new JButton("Crawl");
		record_area = new JTextArea();
		sp = new JScrollPane(record_area);
		result_panel = new JPanel();
		disp_button = new JButton("Display");
		idx = new JTextField();
		disp_lbl = new JLabel("Input the index of record(enter 0 to show all records):");
		result_set = new JTextArea();
		sp2 = new JScrollPane(result_set);
		option_panel = new JPanel();
		
		main_panel.setLayout(new BorderLayout());
		main_panel.add(search_panel, BorderLayout.NORTH);
		main_panel.add(record_panel, BorderLayout.CENTER);
		main_panel.add(result_panel, BorderLayout.SOUTH);
		
		add(main_panel);
		
		search_panel.setLayout(new GridLayout(2,1));
		search_panel.add(search_top_panel);
		search_panel.add(search_bot_panel);
		search_top_panel.add(starting_lbl);
		search_top_panel.add(init_link);
		init_link.setPreferredSize(new Dimension(370,35));
		
		search_bot_panel.add(depth_lbl);
		search_bot_panel.add(init_depth);
		init_depth.setPreferredSize(new Dimension(200,35));
		search_bot_panel.add(begin_button);
		begin_button.setPreferredSize(new Dimension(100,35));
		begin_button.addActionListener(new beginListener("",1,1));
		
		record_panel.add(sp);
		sp.setPreferredSize(new Dimension(400,300));
		sp.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
		
		option_panel.add(disp_lbl);
		option_panel.add(idx);
		idx.setPreferredSize(new Dimension(100,35));
		disp_button.addActionListener(new dispListener());
		option_panel.add(disp_button);
		result_panel.setLayout(new GridLayout(2,1));
		result_panel.add(option_panel);
		result_panel.add(sp2);
		sp2.setPreferredSize(new Dimension(400,100));
		sp2.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
		
		
		setVisible(true);
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		setSize(WIDTH, HEIGHT);
	}
	
	public class dispListener implements ActionListener{
		public void actionPerformed(ActionEvent arg0) {
			try {
				result_set.setText("");
				int index = Integer.parseInt(idx.getText());
				conn = DriverManager.getConnection(filename);
				if(conn != null) {
					result_set.append("connected to database! \nRecords shown in this order: id, crawl_level, children_cnt, url, size(Bytes)\n");
				}
				String readAllSQL = "SELECT * FROM crawl_result";
				String readDataSQL = "SELECT * FROM crawl_result WHERE id = " + index;
				ResultSet rs;
				if(index == 0) {
					rs = statement.executeQuery(readAllSQL);
				}else {
					rs = statement.executeQuery(readDataSQL);
				}
				while(rs.next()) {
					result_set.append(rs.getString(1) + "\t" + rs.getString(2) + "\t" + rs.getString(3) + "\t" + rs.getString(4) + "\t" + rs.getString(5) + "\n");
				}

			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
	}
	
	
	public class beginListener implements ActionListener, Runnable {
		private int current_level;
		private String url;
		private int id;
		
		public beginListener(String url, int current_level, int id){
			this.url = url;
			this.current_level = current_level;
			this.id = id;
		}
		
		public void actionPerformed(ActionEvent arg0) {
			record_area.setText("");
			// create a new database to store future web page data
			LocalDate ld = LocalDate.now();
			LocalTime lt = LocalTime.now();
			String time = 	ld.getYear() + "" + 
								String.format("%02d", ld.getMonthValue()) + "" +
								String.format("%02d", ld.getDayOfMonth()) + "" + 
								String.format("%02d", lt.getHour()) + "" + 
								String.format("%02d", lt.getMinute()) + "" + 
								String.format("%02d", lt.getSecond());
			filename = "jdbc:sqlite:" + time + ".db";
			try {
				Class.forName("org.sqlite.JDBC");
				conn = DriverManager.getConnection(filename);
				if(conn != null) {
					record_area.append("Database: " + time + ".db has been created.\n");
				}
				statement = conn.createStatement();
				String createTableSQL = "CREATE TABLE IF NOT EXISTS crawl_result (" + 
										"id INTEGER PRIMARY KEY," + 
										"crawl_level INTEGER," +
										"children_cnt INTEGER," + 
										"url VARCHAR(100)," + 
										"size INTEGER)";
				statement.executeUpdate(createTableSQL);
			} catch (SQLException e) {
				e.printStackTrace();
			} catch (ClassNotFoundException cnfe) {
				cnfe.printStackTrace();
			}
			
			// perform initial pass of crawling
			depth = Integer.parseInt(init_depth.getText());
			Thread t = new Thread(new beginListener(init_link.getText(),1,1));
			t.start();
		}
		
		public Document request(String url) {
			try {
				org.jsoup.Connection conn = Jsoup.connect(url);
				Document doc = conn.get();
				if(conn.response().statusCode() == 200 && !sync_vL.contains(url) && sync_vL.add(url)) {
					return doc;
				} else {
					return null;
				}
			} catch (IOException e) {
				return null;
			} 
		}
		
		public void crawl(int level) {
			// while it's not too depth, keep crawling
			if(level <= depth) {
				// download the web page and save it in a String 
				Document doc =  request(url);
				if(doc == null) {
					return;
				}
				String page = doc.html();
				Elements links = doc.select("a[href^=http], a[href^=https]");
				int children_cnt = links.size();
				int size = page.getBytes().length;
				String insertLinkSQL = "INSERT INTO crawl_result(crawl_level, children_cnt, url, size) " + 
									   "VALUES(" + current_level + "," + children_cnt + ",\"" + url + "\"," + size + ")";
				try {
					statement.executeUpdate(insertLinkSQL);
				} catch (SQLException e) {
					e.printStackTrace();
				}
				// search all links in current page and crawl them
				for(Element link : doc.select("a[href^=http], a[href^=https]")) {
					String temp_url = link.absUrl("href");
					if(sync_vL.contains(temp_url) == false) {
						record_area.append("Found: " + temp_url + "\n");
						if(current_level <= depth) {
							new Thread(new beginListener(temp_url, current_level+1,this.id+1)).start();
						}
					} else {
						continue;
					}				
				}
			}
		}
		
		public void run() {
			crawl(current_level); // starting crawling at level 1
		}
	}
	
	public static void main(String[] args) {
		new Crawler();
	}
}
