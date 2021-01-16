package health;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;


import org.apache.commons.lang3.StringUtils;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.DateUtils;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicHeader;
import org.apache.http.util.EntityUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.charset.StandardCharsets;
import java.util.*;

public class HealthMain {
    private  static  final Logger logger = LogManager.getLogger(HealthMain.class);

    static Date startDate = DateUtils.parseDate("2020-12-30",new String[]{"yyyy-MM-dd"});;
    static Date endDate = DateUtils.parseDate("2021-12-30",new String[]{"yyyy-MM-dd"});;

    static String token="";
    static String userKey="";
/*    static String depId="200085741"; //儿保科
    static String docId="200195299"; //儿保科*/
    static String depId="200085391"; //FIXED  200085741 9价
    static String docId="200438736"; //FIXED 200195299 9价

    static String cid="";//FIXED
    static String userId="";//FIXED
    static String unitId="";//FIXED
    static String visitType = "0"; //FIXED

    static String mid = "";//FIXED
    static int threadNum = 2;
    static int interval = 250;
    static volatile boolean isSuccess = false;

    public static void main(String[] args) throws InterruptedException {

        logger.info("=================健康160开始=================");

        if (args.length>=9) {
            token = args[0];
            userKey = args[1];
            depId = args[2];
            docId = args[3];
            mid = args[4];
            threadNum = Integer.valueOf(args[5]);
            interval = Integer.valueOf(args[6]);
            startDate = DateUtils.parseDate(args[7],new String[]{"yyyy-MM-dd"});
            endDate = DateUtils.parseDate(args[8],new String[]{"yyyy-MM-dd"});
        }

        logger.info("token："+token);
        logger.info("userKey："+userKey);
        logger.info("depId："+depId);
        logger.info("docId："+docId);
        logger.info("mid："+mid);
        logger.info("startDate："+startDate);
        logger.info("endDate："+endDate);

        logger.info("当前线程数:[{}],一轮次间隔:[{}]毫秒",threadNum,interval);

        for (int i = 0; i < threadNum; i++) {
            Thread thread = new Thread(() -> {
                while (true) {
                    try {
                        if (!isSuccess) {
                            doIt();
                            Thread.sleep(interval);
                        }else{
                            logger.info("抢购已结束.所有线程退出");
                            return;
                        }
                    } catch (Exception ex) {
                        ex.printStackTrace();
                        logger.error(ex.getMessage());
                    }
                }
            });
            thread.start();
            Thread.sleep(interval/threadNum);
        }
        logger.info("=================健康160结束=================");
    }

    private static List<Header> getCommonHeader(){
        List<Header> headers = new ArrayList<>();
        headers.add(new BasicHeader("Host","weapp.91160.com"));
        headers.add(new BasicHeader("Connection","keep-alive"));
        headers.add(new BasicHeader("User-Agent","Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/53.0.2785.143 Safari/537.36 MicroMessenger/7.0.9.501 NetType/WIFI MiniProgramEnv/Windows WindowsWechat"));
        headers.add(new BasicHeader("content-type","application/json"));
        headers.add(new BasicHeader("Referer","https://servicewechat.com/wx41d50f4960b90df8/35/page-frame.html"));
        headers.add(new BasicHeader("Accept-Encoding","gzip, deflate, br"));
        return headers;
    }

    private static  <T> T invokeHTTP(String path,Header extHeader,boolean isStr,Class<T> clz){
        try {
            HttpGet get = new HttpGet(path);
            List<Header> headers = getCommonHeader();
            if(extHeader != null){
                headers.add(extHeader);
            }
            get.setHeaders(headers.toArray(new Header[0]));
            CloseableHttpClient httpClient = HttpClients.createDefault();
            HttpEntity httpEntity = httpClient.execute(get).getEntity();
            String json =  EntityUtils.toString(httpEntity, StandardCharsets.UTF_8);
            return isStr?(T)json: (clz==null?(T)JSONObject.parseObject(json):JSONObject.parseObject(json,clz));
        }catch (Exception ex){
            logger.info(ex);
        }
        return null;
    }

    public static void doIt(){
        logger.info("请求预约日程");
        String scheduleHttpPath="https://weapp.91160.com/doc/schedule.html?token="+token+"&cid="+cid+"&user_id="+userId+"&user_key="+userKey+"&unit_id="+unitId+"&dep_id="+depId+"&doc_id="+docId+"&visit_type="+visitType;
        JSONObject scheduleVar = invokeHTTP(scheduleHttpPath,null,false,null);
        if (scheduleVar!=null) {
            if ("1".equals(scheduleVar.get("status"))||"获取成功".equals(scheduleVar.get("msg"))) {
                logger.info("请求日程成功");
                JSONArray schedulData = scheduleVar.getJSONArray("data");
                if (schedulData!=null) {
                    for (Object datum : schedulData) {
                        JSONObject scheduleItem = (JSONObject) datum;
                        String dateStr = scheduleItem.getString("date");
                        Object am = scheduleItem.get("am");
                        Object pm = scheduleItem.get("pm");
                        if (StringUtils.isNoneEmpty(dateStr)) {
                            Date parseDate = DateUtils.parseDate(dateStr,new String[]{"yyyy-MM-dd"});
                            if (parseDate.compareTo(startDate)>=0 && parseDate.compareTo(endDate)<=0) {
                                //在所选区间内遍历日程
                                logger.info("发现预约日期在所选区间:[{}]",dateStr);
                                //优先下午
                                for (Object varObj : Arrays.asList(am, pm)) {
                                    if (varObj!=null && varObj instanceof JSONObject) {
                                        if (varObj==am) {
                                            logger.info("   正在解析 am");
                                        }else if(varObj==pm){
                                            logger.info("   正在解析 pm");
                                        }

                                        JSONObject var = ((JSONObject) varObj);
                                        String yStateDesc = var.getString("y_state_desc");
                                        String yuyue_num = var.getString("yuyue_num");
                                        String yuyue_max = var.getString("yuyue_max");

                                        yuyue_num=yuyue_num==null||yuyue_num.trim().length()==0?"0":yuyue_num;
                                        yuyue_max=yuyue_max==null||yuyue_max.trim().length()==0?"0":yuyue_max;

                                        if ("可预约".equals(yStateDesc) || Long.valueOf(yuyue_num)<Long.valueOf(yuyue_max)) {
                                            String scheduleId = var.getString("schedule_id");
                                            logger.info("   当前日期可预约,scheduleID:[{}]",scheduleId);
                                            logger.info("   正在解析预约具体时间");
                                            String timeHttpPath="https://weapp.91160.com/doc/detlnew.html?token="+token+"&cid="+cid+"&user_id="+userId+"&user_key="+userKey+"&unit_id="+unitId+"&dep_id="+depId+"&doc_id="+docId+"&schedule_id="+scheduleId;
                                            JSONObject timeVar = invokeHTTP(timeHttpPath,null,false,null);
                                            if (timeVar!=null && "获取成功".equals(timeVar.getString("msg")) || "1".equals(timeVar.getString("status"))) {
                                                Object timeData = timeVar.get("data");
                                                if (timeData instanceof JSONObject) {
                                                    JSONObject jsonObject = ((JSONObject) timeData);
                                                    JSONArray timeArray = jsonObject.getJSONArray(scheduleId);
                                                    if (timeArray==null || isSuccess) {
                                                        continue;
                                                    }
                                                    for (Object timeItemObject : timeArray) {
                                                        JSONObject timeItem = (JSONObject) timeItemObject;
                                                        String detlId = timeItem.getString("detl_id");
                                                        String timeScheduleId = timeItem.getString("schedule_id");
                                                        String detlTimeDesc = timeItem.getString("detl_time_desc");
                                                        String yuyueMax = timeItem.getString("yuyue_max");
                                                        String yuyueNum = timeItem.getString("yuyue_num");
                                                        logger.info("           发现预约时间:[{}],最大数量:[{}],当前数量:[{}]",detlTimeDesc,yuyueMax,yuyueNum);
                                                        yuyueNum=yuyueNum==null||yuyueNum.trim().length()==0?"0":yuyueNum;
                                                        yuyueMax=yuyueMax==null||yuyue_max.trim().length()==0?"0":yuyueMax;
                                                        if (Long.valueOf(yuyueNum)<Long.valueOf(yuyueMax) && isSuccess==false) {
                                                            logger.info("               正在抢购");
                                                            String submitHttpPath="https://weapp.91160.com/order/submit.html?_form_id=99801161bb4642ee84dfb3389d097d72&token="+token+"&cid="+cid+"&user_id="+userId+"&user_key="+userKey+"&mid="+mid+"&hisMemId=&mobile=15626869864&doc_id="+docId+"&unit_id="+unitId+"&dep_id="+depId+"&sch_id="+timeScheduleId+"&detl_id="+detlId+"&guardian_member_id=0&platformPassword=&counties_id=18351&member_address=null";
                                                            JSONObject submitResult = invokeHTTP(submitHttpPath, null, false, null);//TODO
                                                            if (submitResult!=null) {
                                                                String msg = submitResult.getString("msg");
                                                                String orderId = submitResult.getString("order_id");
                                                                if ("预约成功".equals(msg)) {
                                                                    isSuccess = true;
                                                                    logger.info("               抢购结束,状态:[{}],订单ID:[{}]","成功",orderId);
                                                                    return;
                                                                }
                                                            }
                                                            logger.info("               抢购结束,状态:[{}],info:[{}]","失败",submitResult);
                                                        }
                                                    }
                                                }
                                            }
                                        }else{
                                            logger.info("   不可预约,num:[{}],max:[{}]",yuyue_num,yuyue_max);
                                        }
                                    }else{
                                        if (varObj==am) {
                                            logger.info("   am 该日程的信息为空");
                                        }else if(varObj==pm){
                                            logger.info("   pm 该日程的信息为空");
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
