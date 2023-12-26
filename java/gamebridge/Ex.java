package gamebridge;

@FunctionalInterface
public interface Ex {
	
	public void invoke() throws Exception;
	
	public static void run(Ex ex) {
		try {
			ex.invoke();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

}
