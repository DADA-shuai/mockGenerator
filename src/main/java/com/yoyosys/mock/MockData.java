package com.yoyosys.mock;


import com.yoyosys.mock.pojo.Column;
import com.yoyosys.mock.pojo.DataSourceConfig;
import com.yoyosys.mock.pojo.DsConfig;
import com.yoyosys.mock.pojo.DsDlpMockDataConfig;
import com.yoyosys.mock.util.Constants;
import com.yoyosys.mock.util.JsqlparserUtil;
import com.yoyosys.mock.util.MakeDataUtil;
import com.yoyosys.mock.util.ModifyDataUtil;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.Expression;

import java.io.*;
import java.sql.*;
import java.util.*;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;


/**
 * @Author: yjj
 * Date: 2021/9/3
 */
public class MockData {
    public static void main(String[] args) {
        MockData mockData = new MockData();
        //读取模拟数据配置文件（dlp_yoyo_mockdata.config）：数据库连接信息
        DataSourceConfig dataSourceConfig = mockData.getDataSourceConfig();
        //读取配置表中的配置信息：查询配置表中与操作人匹配且状态为‘0’（未执行）的数据行存放到配置类中
        List<DsDlpMockDataConfig> dsDlpMockDataConfigs = mockData.getDsDlpMockDataConfig(dataSourceConfig);

        ModifyDataUtil modifyDataUtil = new ModifyDataUtil();
        mockData.makeData(mockData, dataSourceConfig, dsDlpMockDataConfigs, modifyDataUtil);

    }

    //根据配置生成数据
    private boolean makeData(MockData mockData, DataSourceConfig dataSourceConfig,
            List<DsDlpMockDataConfig> dsDlpMockDataConfigs, ModifyDataUtil modifyDataUtil){
        for (DsDlpMockDataConfig dsDlpMockDataConfig : dsDlpMockDataConfigs) {
            //读取表结构：获取配置类中的表名，根据表名去DS_CONFIG中查找数据加载场景(LOAD_SCENE)
            DsConfig dsConfig = mockData.getDsConfig(dataSourceConfig, dsDlpMockDataConfig.getHive_name());

            //todo: 王震宣  解析模板文件

            //模拟数据集
            LinkedHashMap<Column, List> resultMap = new LinkedHashMap<>();
            String hiveName = dsDlpMockDataConfig.getHive_name();
            //数据加载场景
            String loadScene = dsConfig.getLoadScene().substring(0, 2);
            //开始时间
            String startDate = dsDlpMockDataConfig.getStartDate();
            //结束时间
            String endDate = dsDlpMockDataConfig.getEndDate();

            StringBuilder modeFile = new StringBuilder("D:\\work_space\\mock_data\\data");//表结构存储文件路径
            StringBuilder modeFile1 = new StringBuilder("D:\\work_space\\mock_data\\data");//存量文件，用来确定分区字段
            //根据数据加载场景去对应的目录下查找该表对应的以ext开头的模板文件
            switch (loadScene) {//数据加载场景
                case Constants.LOADSCENE01:
                    modeFile.append("/B1/" + hiveName + "/" + "load_" + hiveName + ".tpl");
                    modeFile1.append("/B1_C/" + hiveName + "/" + "load_" + hiveName + ".tpl");

                    break;
                case Constants.LOADSCENE02:
                    modeFile.append("/B2/" + hiveName + "/" + "load_" + hiveName + ".tpl");
                    modeFile1.append("/B2_C/" + hiveName + "/" + "load_" + hiveName + ".tpl");
                    break;
                case Constants.LOADSCENE03:
                    modeFile.append("/B3/" + hiveName + "/" + "ext_" + hiveName + ".tpl");
                    modeFile1 = null;
                    break;
                case Constants.LOADSCENE04:
                    modeFile.append("/B4/" + hiveName + "/" + "ext_" + hiveName + ".tpl");
                    modeFile1 = null;
                    break;
            }
            //表结构
            List<Column> columnList = mockData.getColumn(modeFile, modeFile1, loadScene);

            //是否上传数据文件：读取数据文件/mockdata/data/a_pdata_t03_agmt_fea_rela_h_2021070 9_000_000.dat
            if (getDataFile()) {
                //生成模拟数据集
                StringBuilder result1 = new StringBuilder();
                try {
                    BufferedReader bfr1 = new BufferedReader(
                        new InputStreamReader(new FileInputStream(new File(
                            "D:\\work_space\\mock_data\\data\\a_pdata_t03_agmt_fea_rela_h_20210709_000_000.dat")), "UTF-8"));
                    String lineTxt1 = null;
                    while ((lineTxt1 = bfr1.readLine()) != null) {
                        result1.append(lineTxt1).append("\n");
                    }
                    bfr1.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }

                String[] createSql1 = result1.toString().split("\n");
                List<String[]> listb = new ArrayList<>();
                for (String a : createSql1) {
                    String[] b = a.split("\\|\\+\\|");
                    listb.add(b);
                }

                for (int j = 0; j < columnList.size(); j++) {
                    List list = new ArrayList();
                    for (int i = 0; i < listb.size(); i++) {
                        String[] arr = listb.get(i);
                        list.add(arr[j]);
                    }
                    resultMap.put(columnList.get(j), list);
                }

            } else {
                //没有数据文件,获取表主键
                try {
                    //设置主键标识
                    List<String> primaryKeyList = mockData.getPrimayKey();//没有数据文件,获取表主键
                    //生成模拟数据集
                    for (Column column : columnList) {
                        for (String primaryKey : primaryKeyList) {
                            if (column.getFieldName().toUpperCase().equals(primaryKey.toUpperCase())) {
                                column.setPrimaryKey(true);
                            } else {
                                column.setPrimaryKey(false);
                            }
                        }
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                }
                //生成记录条数
                int records = dsDlpMockDataConfig.getRecords();
                resultMap = mockData.createData(columnList, records, startDate, endDate, loadScene);
            }

            /*
             * 根据条件修改数据
             * 1.解析where条件
             * 2.修改数据
             * todo:易建军、王燚
             *  return : list<map>
             * */

            try {
                List<Expression> sqlParser = JsqlparserUtil.getSQLParser(dsDlpMockDataConfig.getConditions());
                LinkedHashMap<Column, List> columnListMap = modifyDataUtil.modifyData(resultMap, columnList, sqlParser);
            } catch (JSQLParserException e) {
                e.printStackTrace();
            }

            /**
             * 输出
             创建输出路径：/result
             创建输出文件：i_pdata_t03_agmt_fea_rela_h_20210709_000_000.dat
             压缩输出文件：i_pdata_t03_agmt_fea_rela_h_20210709_000_000.dat.Z
             创建加载完成的就绪文件：i_pdata_t03_agmt_fea_rela_h_20210709_000_000.xml
             删除输出的dat文件
             修改状态：修改配置类中状态属性为‘1’（成功），写回到对应配置表中。
             *
             * todo:王锦鹏
             * */

            //String filePath = new MockData().getClass().getResource("/").getPath() + "\\result";
            String filePath = dataSourceConfig.getResultFilePath();
            String start_date = dsDlpMockDataConfig.getStartDate();
            String hive_name = dsDlpMockDataConfig.getHive_name();
            String charsetName = dsConfig.getFILE_ENCODING();
            int ID =dsDlpMockDataConfig.getId();
            String alikeFileName="i_"+ hive_name + "_" + start_date + "_000_";
            outPutFile(alikeFileName,charsetName,filePath,ID,resultMap);
        }
        return true;
    }

    private static void outPutFile(String alikeFileName,String charsetName,String filePath,int ID,LinkedHashMap<Column, List> resultMap){
        try {
            String fileName;

            File[] allfiles = new File(filePath).listFiles();
            int count=0;
            for (File file : allfiles) {
                if (file.getName().contains(alikeFileName)){
                    count++;
                }
            }
            int i = count / 2;
            String num;
            if (i <10){
                num="00"+ i;
            }else if (i>=100){
                num=String.valueOf(i);
            }else{
                num="0"+i;
            }

            fileName=filePath+File.separator + alikeFileName+num+".dat";

            OutPutFile.generateDatFile(fileName, charsetName, resultMap);
            OutPutFile.compressFile(fileName, filePath);
            long size = (new File(fileName).length());
            OutPutFile.createXml(fileName,size,filePath,charsetName);
            OutPutFile.deleteFile(fileName);
            OutPutFile.update(ID);
        } catch (IOException e) {
            e.printStackTrace();

            } catch (SQLException e) {
                e.printStackTrace();
            }
        }


    //根据表结构生成模拟数据
    private LinkedHashMap<Column, List> createData (List <Column> columnList, int records,
                                                    String startDate, String endDate,
                                                    String loadSence){
        //模拟数据集
        LinkedHashMap<Column, List> resultMap = new LinkedHashMap<>();

        //天数
        int betweenDays = Integer.parseInt(endDate) - Integer.parseInt(startDate) + 1;
        int n = 0;
        if (loadSence.equals(Constants.LOADSCENE04)){
            n = records * (betweenDays + 1);
        }

        for (Column column : columnList) {
            List list = new ArrayList();

            for (int i = 0; i < records; i++) {
                switch (column.getcType().toUpperCase()) {
                    case "CHAR"://
                        list.add(MakeDataUtil.makeCharData());
                        break;
                    case "VARCHAR"://可变长度的字符串
                    case "NCHAR"://根据字符集而定的固定长度字符串
                    case "NVARCHAR2 "://根据字符集而定的可变长度字符串
                    case "STRING":
                    case "LONG"://超长字符串
                        //日期型
                        if (column.getIsPartition()) {
                            if (loadSence.equals(Constants.LOADSCENE04)){
                                if (i < n / (betweenDays + 1)){
                                    if (column.getFieldName().toUpperCase().equals(Constants.END_DT)){
                                        list.add(Constants.MAX_DT);
                                        break;
                                    }
                                    list.add(MakeDataUtil.makeDateData(startDate,endDate));
                                    break;
                                }else{
                                    list.add( Integer.parseInt(startDate) + (i / records % 10));
                                }
                            }
                            list.add(MakeDataUtil.makeDateData(startDate,endDate));
                            break;
                        }
                        list.add(MakeDataUtil.makeStringData(column));
                        break;
                    case "INTEGER":
                        list.add(MakeDataUtil.makeIntData(column));
                        break;
                    case "NUMBER":
                    case "DECIMAL":
                    case "FLOAT":
                        list.add(MakeDataUtil.makeNumData(column));
                        break;
                }
            }
            resultMap.put(column, list);
        }
        return resultMap;
    }

    //判断是否上传数据文件
    private static boolean getDataFile () {
        File file = new File("D:\\work_space\\mock_data\\data\\a_pdata_t03_agmt_fea_rela_h_20210709_000_000.dat");
        if (!file.exists()) {
            return false;
        }
        return true;
    }


    //获取表结构
    private List<Column> getColumn (StringBuilder filePath, StringBuilder filePath1, String loadScene){
        //字段类集合
        List<Column> columnList = new ArrayList<>();
        StringBuilder result = new StringBuilder();
        StringBuilder result1 = new StringBuilder();
        try {
            //读取表结构文件
            BufferedReader bfr = new BufferedReader(
                new InputStreamReader(new FileInputStream(
                    new File(filePath.toString())), "UTF-8"));
            String lineTxt = null;
            while ((lineTxt = bfr.readLine()) != null) {
                result.append(lineTxt).append("\n");
            }
            bfr.close();
            if (filePath1 != null) {
                //读取存量文件
                BufferedReader bfr1 = new BufferedReader(
                        new InputStreamReader(new FileInputStream(
                                new File(filePath1.toString())), "UTF-8"));
                String lineTxt1 = null;
                while ((lineTxt1 = bfr.readLine()) != null) {
                    result1.append(lineTxt1).append("\n");
                }
                bfr1.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        String[] createSql = result.toString().split("\n");
        int startAndEnd[] = new int[2];
        int index = 0;
        //取出create后，where前的定义字段部分
        for (int i = 0; i < createSql.length; i++) {
            if (createSql[i].substring(0, 1).equals("(") || createSql[i].substring(1, 2).equals(")")) {
                startAndEnd[index] = i;
                index++;
            }
        }

        for (int i = startAndEnd[0] + 1; i < startAndEnd[1]; i++) {
            Column column = new Column();
            String[] sqlStructure = createSql[i].split(" ");
            //设置字段名
            column.setFieldName(sqlStructure[0]);
            //设置字段是否为分区字段
            String partitionField = null;
            switch (loadScene) {//数据加载场景
                case Constants.LOADSCENE01:
                case Constants.LOADSCENE02:
                    String[] sql = result1.toString().split("\n");
                    for (String s : sql) {
                        if (s.toLowerCase().contains(Constants.AS_BDAP_ETLDATE)) {
                            String[] s1 = s.split(" ");
                            partitionField = s1[0];
                        }
                    }
                    if (column.getFieldName().toUpperCase().equals(partitionField.toUpperCase())) {
                        column.setPartition(true);
                    } else {
                        column.setPartition(false);
                    }
                    break;
                case Constants.LOADSCENE03:
                    column.setPartition(false);
                    break;
                case Constants.LOADSCENE04:
                    if (sqlStructure[0].toUpperCase().equals(Constants.START_DT) ||
                            sqlStructure[0].toUpperCase().equals(Constants.END_DT)) {
                        column.setPartition(true);
                    } else {
                        column.setPartition(false);
                    }
                    break;
            }
            int n = sqlStructure[1].indexOf("(");
            int m = sqlStructure[1].indexOf(")");
            String cType = "";   //字段类型
            String cLength = ""; //字段长度
            if (n == -1) {
                cType = sqlStructure[1];
            } else {
                cType = sqlStructure[1].substring(0, n);
                cLength = sqlStructure[1].substring(n + 1, m);
            }
            //设置字段类型，长度
            column.setcType(cType);
            column.setcLength(cLength);
            columnList.add(column);
        }
        return columnList;
    }


    /**
     * 将文件中获取到的主键放入list
     *
     * @return
     * @throws Exception
     */
    private List<String> getPrimayKey() throws IOException {
//        String path = this.getClass().getClassLoader().getResource("D:\\work_space\\mock_data\\data\\loadone_pdata_t03_agmt_fea_rela_h.tpl").getFile();
        FileReader fileReader = new FileReader("D:\\work_space\\mock_data\\data\\loadone_pdata_t03_agmt_fea_rela_h.tpl");
        BufferedReader bfReader = new BufferedReader(fileReader);
        String temp = null;
        StringBuffer sb = new StringBuffer();
        int startWtih = 0;
        int endWith = 0;
        int line = 1;
        while ((temp = bfReader.readLine()) != null) {
            if (temp != null && temp.length() > 0) {
                sb.append(temp);
            }
            if (sb.toString().contains("on")) {
                startWtih = sb.indexOf("on");
            }
            if (sb.toString().contains("where")) {
                endWith = sb.indexOf("where", startWtih);
            }
        }
        String subStr = sb.toString().substring(startWtih, endWith);
        List<String> listStr = new ArrayList<String>();
        String[] arrStr = subStr.split(" ");
        for (int i = 0; i < arrStr.length; i++) {
            if (arrStr[i].contains("<=>")) {
                String str = arrStr[i].substring(arrStr[i].indexOf(".") + 1, arrStr[i].indexOf("<"));
                listStr.add(str);
            }
        }
        bfReader.close();
        fileReader.close();
        return listStr;
    }



    /*
    读取配置文件
      *todo:宋金城
      *输入：
      *return: DataSourceConfig
     */
    public DataSourceConfig getDataSourceConfig () {
        DataSourceConfig dataSourceConfig = null;
        //1.获取当前jar包路径
        File rootPath = new File(this.getClass().getResource("/").getPath());//此路径为当前项目路径
        System.out.println("当前环境根节点目录" + rootPath);
        //2.拼接路径
        String path = rootPath + "\\mock_data\\conf\\dlp_yoyo_mockdata.config";
        System.out.println(path);
        String configPath = "D:\\work_space\\mock_data\\conf\\dlp_yoyo_mockdata.config";
        //3.获取配置文件信息
        try {
            InputStream in = new FileInputStream(configPath);
            Properties p = new Properties();
            p.load(in);
            //4.获取配置文件信息
            String oracle_driver = p.getProperty("Oracle_driver");
            String oracle_url = p.getProperty("Oracle_url");
            String oracle_user = p.getProperty("Oracle_user");
            String oracle_pasd = p.getProperty("Oracle_password");
            String Operator = p.getProperty("Operator");
            String Timestamp = p.getProperty("Timestamp");
            String ModeFilePath = p.getProperty("ModeFilePath");
            String DataFilePath = p.getProperty("DataFilePath");
            String ResultFilePath = p.getProperty("ResultFilePath");
            dataSourceConfig = new DataSourceConfig();
            dataSourceConfig.setOracle_driver(oracle_driver);
            dataSourceConfig.setOracle_url(oracle_url);
            dataSourceConfig.setOracle_user(oracle_user);
            dataSourceConfig.setOracle_password(oracle_pasd);
            dataSourceConfig.setOperator(Operator);
            dataSourceConfig.setTimestamp(Timestamp);
            dataSourceConfig.setModeFilePath(ModeFilePath);
            dataSourceConfig.setDataFilePath(DataFilePath);
            dataSourceConfig.setResultFilePath(ResultFilePath);
            return dataSourceConfig;
        } catch (IOException io) {
            System.out.println("读取配置文件异常" + io);
            return null;
        }
    }

    /**
     * todo: 宋金城
     * param: operator
     * return: DsDlpMockDataConfig
     */
    public List<DsDlpMockDataConfig> getDsDlpMockDataConfig(DataSourceConfig dataSourceConfig) {
        List<DsDlpMockDataConfig> dsDlpMockDataConfigList = new ArrayList<>();
        if (dataSourceConfig != null) {
            Connection connection = null;
            PreparedStatement mockDataConfigPs = null;
            ResultSet mockDataConfigResultSet = null;
            try {
                connection = getConnection(dataSourceConfig);
                String mockDataConfigSql = "select * from DS_DLP_MOCKDATA_CONFIG where OPERATOR = ? and STATE = 0";
                mockDataConfigPs = connection.prepareStatement(mockDataConfigSql);
                mockDataConfigPs.setString(1, dataSourceConfig.getOperator());
                mockDataConfigResultSet = mockDataConfigPs.executeQuery();

                while (mockDataConfigResultSet.next()) {
                    DsDlpMockDataConfig dsDlpMockDataConfig = new DsDlpMockDataConfig();
                    dsDlpMockDataConfig.setId(mockDataConfigResultSet.getInt("ID"));

                    dsDlpMockDataConfig.setHive_name(mockDataConfigResultSet.getString("HIVE_NAME"));
                    // String CONDITIONS = mockDataConfigResultSet.getString("CONDITIONS");
                    dsDlpMockDataConfig.setConditions(mockDataConfigResultSet.getString("CONDITIONS"));
                    // int  RECORDS = mockDataConfigResultSet.getInt("RECORDS");
                    dsDlpMockDataConfig.setRecords(mockDataConfigResultSet.getInt("RECORDS"));
                    // Date START_DATE = mockDataConfigResultSet.getDate("START_DATE");
                    dsDlpMockDataConfig.setStartDate(mockDataConfigResultSet.getString("START_DATE"));
                    //Date END_DATE = mockDataConfigResultSet.getDate("END_DATE");
                    dsDlpMockDataConfig.setEndDate(mockDataConfigResultSet.getString("END_DATE"));
                    //String INPUT_DIR = mockDataConfigResultSet.getString("INPUT_DIR");
                    dsDlpMockDataConfig.setInput_dir(mockDataConfigResultSet.getString("INPUT_DIR"));
                    // Date ONLINE_DATE = mockDataConfigResultSet.getDate("ONLINE_DATE");
                    dsDlpMockDataConfig.setOnline_date(mockDataConfigResultSet.getDate("ONLINE_DATE"));
                    //String OPERATOR = mockDataConfigResultSet.getString("OPERATOR");
                    dsDlpMockDataConfig.setOperator(mockDataConfigResultSet.getString("OPERATOR"));
                    //int  STATE = mockDataConfigResultSet.getInt("STATE");
                    dsDlpMockDataConfig.setState(mockDataConfigResultSet.getInt("STATE"));
                    //Date CREATE_TIME = mockDataConfigResultSet.getDate("CREATE_TIME");
                    dsDlpMockDataConfig.setCreate_time(mockDataConfigResultSet.getDate("CREATE_TIME"));
                    //String  DS_NAME = mockDataConfigResultSet.getString("DS_NAME");
                    dsDlpMockDataConfig.setDs_name(mockDataConfigResultSet.getString("DS_NAME"));
                    //String  NAME_EN = mockDataConfigResultSet.getString("NAME_EN");
                    dsDlpMockDataConfig.setName_en(mockDataConfigResultSet.getString("NAME_EN"));
                    //放入到集合当中
                    dsDlpMockDataConfigList.add(dsDlpMockDataConfig);

                }
            } catch (SQLException e4) {
                System.out.println("获取数据库连接失败" + e4);
            } finally {
                close(connection, mockDataConfigPs, mockDataConfigResultSet);
                System.out.println("关闭资源成功");
            }
        }

        return dsDlpMockDataConfigList;
    }

    /**
     * todo: 宋金城
     * param:
     * return: DsConfig
     */
    public DsConfig getDsConfig(DataSourceConfig dataSourceConfig, String table_name) {
        if (table_name != null && table_name != "") {
            Connection connection = null;
            PreparedStatement mockDataConfigPs = null;
            ResultSet mockDataConfigResultSet = null;
            DsConfig dsConfig = null;
            String DS_NAME = null;
            String DS_IDENTIFY = null;
            if (table_name.split("_").length > 0) {
                DS_NAME = table_name.split("_")[0];
                DS_IDENTIFY = table_name.substring(DS_NAME.length() + 1);
            }
            try {
                connection = getConnection(dataSourceConfig);
                String dsCOnfigSql = "select LOAD_SCENE , FILE_ENCODING from DS_CONFIG  where DS_NAME = ?  and DS_IDENTIFY = ?";
                PreparedStatement dsConfigPs = connection.prepareStatement(dsCOnfigSql);
                dsConfigPs.setString(1, DS_NAME);
                dsConfigPs.setString(2, DS_IDENTIFY);
                //todo:获取数据加载场景
                ResultSet dsConfigResultSet = dsConfigPs.executeQuery();
                dsConfig = new DsConfig();
                while (dsConfigResultSet.next()) {
                    dsConfig.setLoadScene(dsConfigResultSet.getString("LOAD_SCENE"));
                    dsConfig.setFILE_ENCODING(dsConfigResultSet.getString("FILE_ENCODING"));

                }
                return dsConfig;
            } catch (SQLException e4) {
                System.out.println(e4);
                return dsConfig;
            } finally {
                close(connection, mockDataConfigPs, mockDataConfigResultSet);
                System.out.println("关闭资源成功");
            }
        }
        return null;
    }

    /**
     * todo: 宋金城 获取数据库连接
     *
     * @param dataSourceConfig
     * @return
     */
    public static Connection getConnection(DataSourceConfig dataSourceConfig) {
        Connection connection = null;

        if (dataSourceConfig != null) {
            try {
                ////
                //1.加载驱动

                System.out.println("加载驱动");
                Class.forName("oracle.jdbc.driver.OracleDriver").newInstance();
                //2.获取连接
                System.out.println("获取连接");
                connection = DriverManager.getConnection(dataSourceConfig.getOracle_url(), dataSourceConfig.getOracle_user(), dataSourceConfig.getOracle_password());
                System.out.println("获取连接成功");
            } catch (InstantiationException e1) {
                e1.printStackTrace();
                System.out.println("实例异常" + e1);

            } catch (IllegalAccessException e2) {
                e2.printStackTrace();
                System.out.println("访问异常" + e2);
            } catch (ClassNotFoundException e3) {
                e3.printStackTrace();
                System.out.println("驱动类找不到" + e3);
            } catch (SQLException e4) {
                e4.printStackTrace();
                System.out.println("获取数据库连接失败" + e4);
            }
        }

        return connection;
    }

    /**
     * todo:宋金城  数据库释放资源
     *
     * @param conn
     * @param ps
     * @param rs
     */
    public static void close(Connection conn, PreparedStatement ps, ResultSet rs) {
        try {
            if (rs != null) {
                rs.close();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        try {
            if (ps != null) {
                ps.close();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        try {
            if (conn != null) {
                conn.close();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

    }


}
