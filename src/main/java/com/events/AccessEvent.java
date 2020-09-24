package com.events;

import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Objects;

/**
 *  AccessEvent 刷卡访问事件的实体类对象
 * */
//@Data
//@AllArgsConstructor
@NoArgsConstructor
public class AccessEvent implements Serializable {
    public Integer id;
    public Integer door_id;
    public String door_status;
    public Integer event_type;
    public String employee_sys_no;
    public String datetime;

    public AccessEvent(AccessEvent indoor) {
    }


    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getDoor_id() {
        return door_id;
    }

    public void setDoor_id(int door_id) {
        this.door_id = door_id;
    }

    public String getDoor_status() {
        return door_status;
    }

    public void setDoor_status(String door_status) {
        this.door_status = door_status;
    }

    public int getEvent_type() {
        return event_type;
    }

    public void setEvent_type(int event_type) {
        this.event_type = event_type;
    }

    public String getEmployee_sys_no() {
        return employee_sys_no;
    }

    public void setEmployee_sys_no(String employee_sys_no) {
        this.employee_sys_no = employee_sys_no;
    }

    public String getDatetime() {
        return datetime;
    }

    public void setDatetime(String datetime) {
        this.datetime = datetime;
    }

    public AccessEvent(int id, int door_id, String door_status, int event_type, String employee_sys_no, String datetime) {
        this.id = id;
        this.door_id = door_id;
        this.door_status = door_status;
        this.event_type = event_type;
        this.employee_sys_no = employee_sys_no;
        this.datetime = datetime;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AccessEvent that = (AccessEvent) o;
        return id == that.id &&
                door_id == that.door_id &&
                door_status == that.door_status &&
                event_type == that.event_type &&
                employee_sys_no == that.employee_sys_no &&
                Objects.equals(datetime, that.datetime);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, door_id, door_status, event_type, employee_sys_no, datetime);
    }
    @Override
    public String toString() {
        return "AccessEvent{" +
                "id=" + id +
                ", door_id=" + door_id +
                ", door_status=" + door_status +
                ", event_type=" + event_type +
                ", employee_sys_no=" + employee_sys_no +
                ", datetime='" + datetime + '\'' +
                '}';
    }
}
