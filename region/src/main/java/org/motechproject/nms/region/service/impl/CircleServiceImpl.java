package org.motechproject.nms.region.service.impl;

import org.motechproject.nms.region.domain.Circle;
import org.motechproject.nms.region.repository.CircleDataService;
import org.motechproject.nms.region.service.CircleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service("circleService")
public class CircleServiceImpl implements CircleService {
    @Autowired
    private CircleDataService circleDataService;

    /**
     * Returns the circle for a given name
     *
     * @param name the circle name
     * @return the circle object if found
     */
    @Override
    public Circle getByName(String name) {
        return circleDataService.findByName(name);
    }

    /**
     * Returns all circles in the database
     *
     * @return all the circles in the database
     */
    @Override
    public List<Circle> getAll() {
        return circleDataService.retrieveAll();
    }

}
