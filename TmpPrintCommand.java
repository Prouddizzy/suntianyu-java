import com.fasterxml.jackson.databind.ObjectMapper;
import com.suntianyu.stm32smartdisinfectorjava.model.dto.DeviceCommand;

public class TmpPrintCommand {
  public static void main(String[] args) throws Exception {
    ObjectMapper mapper = new ObjectMapper();
    DeviceCommand cmd = DeviceCommand.builder()
      .type("control_cmd")
      .cmdId("demo-cmd")
      .deviceId("STM-001")
      .action("start")
      .mode("smart")
      .duration(0)
      .tempLow(18.0)
      .tempHigh(34.0)
      .humidityLow(45.0)
      .humidityHigh(65.0)
      .build();
    System.out.println(mapper.writeValueAsString(cmd));
  }
}
