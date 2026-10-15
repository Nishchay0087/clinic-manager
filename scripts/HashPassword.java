import org.mindrot.jbcrypt.BCrypt;
import java.util.Arrays;
class HashPassword { public static void main(String[] args) {var console=System.console();if(console==null)throw new IllegalStateException("Run in an interactive terminal.");char[] password=console.readPassword("Password (10+ characters, max 72 UTF-8 bytes): ");String value=new String(password);Arrays.fill(password,'\0');if(value.length()<10||value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>72)throw new IllegalArgumentException("Invalid length.");System.out.println(BCrypt.hashpw(value,BCrypt.gensalt(12)));}}
