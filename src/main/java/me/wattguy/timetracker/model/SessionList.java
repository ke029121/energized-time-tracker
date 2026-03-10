package me.wattguy.timetracker.model;

import lombok.Data;
import java.util.List;

@Data
public class SessionList {
    private List<Session> sessions;
}