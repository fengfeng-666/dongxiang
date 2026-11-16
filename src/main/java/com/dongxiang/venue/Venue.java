package com.dongxiang.venue;

import com.baomidou.mybatisplus.annotation.TableName;

@TableName("venue")
public class Venue {
    public Long id; public String name; public String city; public String address; public String category; public String description;
    public Long getId() { return id; } public void setId(Long id) { this.id=id; }
    public String getName() { return name; } public void setName(String name) { this.name=name; }
    public String getCity() { return city; } public void setCity(String city) { this.city=city; }
    public String getAddress() { return address; } public void setAddress(String address) { this.address=address; }
    public String getCategory() { return category; } public void setCategory(String category) { this.category=category; }
    public String getDescription() { return description; } public void setDescription(String description) { this.description=description; }
}
