import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.border.Border;

public class javaclinet extends JFrame{
    private JButton conn = new JButton("connect");
	private JTextArea txt = new JTextArea("");
	private JTextField edt = new JTextField("");
	private JButton send = new JButton("sending");
	private JPanel p1 = new JPanel();
	private JPanel p2 = new JPanel();
	private ExecutorService exec= null;
	private Socket clientSocket = null;
	private DataInputStream fromServer;
	private DataOutputStream toServer;
	
	public javaclinet(){
		setLayout(new BorderLayout());
		p1.setLayout(new BorderLayout());
		p2.setLayout(new BorderLayout());
		p1.add(edt,BorderLayout.CENTER);
		p1.add(send,BorderLayout.EAST);
		
		p2.add(new JScrollPane(txt),BorderLayout.CENTER);
		p2.add(p1,BorderLayout.SOUTH);
		p2.add(conn,BorderLayout.NORTH);

		this.add(p2,BorderLayout.CENTER);
		setSize(500,300);
		setVisible(true);
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		
		conn.addActionListener(new ActionListener() {		
			@Override
			public void actionPerformed(ActionEvent e) {
                exec = Executors.newCachedThreadPool();
                exec.execute(new ClientSocket());
			}
		});
		

		send.addActionListener(new ActionListener() {		
			@Override
			public void actionPerformed(ActionEvent e) {
				try {
                    toServer.writeUTF(edt.getText());
                    toServer.flush();
                    
                    edt.setText("");
				} catch (UnknownHostException e1) {
					e1.printStackTrace();
				} catch (IOException e1) {
					e1.printStackTrace();
				}	
			}
		});
		
	}
	
	class ClientSocket implements Runnable{	
		@Override
		public void run() {
			try {
				clientSocket = new Socket("localhost", 54321);
                fromServer = new DataInputStream(clientSocket.getInputStream());
                toServer = new DataOutputStream(clientSocket.getOutputStream());
				
				while (true) {
                    String msg = fromServer.readUTF();
                    if(msg != null)
                        txt.append(msg+"\n");
				}
			} catch (UnknownHostException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}finally{
                try {
                    if (fromServer != null)
                        fromServer.close();
                    if (toServer != null)
                        toServer.close();
                    if (clientSocket != null)
                        clientSocket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }			
		}
	}
	
	public static void main(String[] args) {
		javaclinet client = new javaclinet();
	}
}
