/*
 * PowerAuth Server and related software components
 * Copyright (C) 2025 Wultra s.r.o.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package com.wultra.security.powerauth.app.server.controller.api.v4;

import com.wultra.core.rest.model.base.request.ObjectRequest;
import com.wultra.core.rest.model.base.response.ObjectResponse;
import com.wultra.security.powerauth.app.server.service.behavior.tasks.ApplicationServiceBehavior;
import com.wultra.security.powerauth.app.server.service.behavior.tasks.v4.ApplicationDetailServiceBehavior;
import com.wultra.security.powerauth.client.model.request.CreateApplicationRequest;
import com.wultra.security.powerauth.client.model.request.GetApplicationDetailRequest;
import com.wultra.security.powerauth.client.model.request.LookupApplicationByAppKeyRequest;
import com.wultra.security.powerauth.client.model.response.CreateApplicationResponse;
import com.wultra.security.powerauth.client.model.response.v4.GetApplicationDetailResponse;
import com.wultra.security.powerauth.client.model.response.GetApplicationListResponse;
import com.wultra.security.powerauth.client.model.response.LookupApplicationByAppKeyResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static net.logstash.logback.argument.StructuredArguments.kv;

/**
 * Controller managing the endpoints related to applications.
 *
 * @author Petr Dvorak, petr@wultra.com
 */
@RestController("applicationControllerV4")
@RequestMapping("/rest/v4/application")
@Tag(name = "PowerAuth Application Controller")
@AllArgsConstructor
@Validated
@Slf4j
public class ApplicationController {

    private final ApplicationServiceBehavior applicationService;
    private final ApplicationDetailServiceBehavior applicationDetailService;

    /**
     * Get the list of applications.
     *
     * @return Application list response.
     * @throws Exception In case the service throws exception.
     */
    @PostMapping("/list")
    public ObjectResponse<GetApplicationListResponse> getApplicationList() throws Exception {
        logger.info("", kv("action", "getApplicationList"), kv("state", "initiated"));
        logger.debug("action: getApplicationList, state: initiated, request: empty");
        final ObjectResponse<GetApplicationListResponse> response = new ObjectResponse<>(applicationService.getApplicationList());
        logger.info("", kv("action", "getApplicationList"), kv("state", "succeeded"));
        logger.debug("action: getApplicationList, state: succeeded, response: {}", response);
        return response;
    }

    /**
     * Create a new application.
     *
     * @param request Create application request.
     * @return Create application response.
     * @throws Exception In case the service throws exception.
     */
    @PostMapping("/create")
    public ObjectResponse<CreateApplicationResponse> createApplication(@Valid @RequestBody ObjectRequest<CreateApplicationRequest> request) throws Exception {
        final CreateApplicationRequest req = request.getRequestObject();
        logger.info("", kv("action", "createApplication"), kv("state", "initiated"), kv("applicationId", req.getApplicationId()));
        logger.debug("action: createApplication, state: initiated", kv("applicationId", req.getApplicationId()), kv("request", request));
        final ObjectResponse<CreateApplicationResponse> response = new ObjectResponse<>(applicationService.createApplication(req));
        logger.info("", kv("action", "createApplication"), kv("state", "succeeded"), kv("applicationId", req.getApplicationId()));
        logger.debug("action: createApplication, state: succeeded", kv("applicationId", req.getApplicationId()), kv("response", response));
        return response;
    }

    /**
     * Fetch application detail.
     *
     * @param request Application detail request.
     * @return Application detail response.
     * @throws Exception In case the service throws exception.
     */
    @PostMapping("/detail")
    public ObjectResponse<GetApplicationDetailResponse> getApplicationDetail(@Valid @RequestBody ObjectRequest<GetApplicationDetailRequest> request) throws Exception {
        final GetApplicationDetailRequest req = request.getRequestObject();
        logger.info("", kv("action", "getApplicationDetail"), kv("state", "initiated"), kv("applicationId", req.getApplicationId()));
        logger.debug("action: getApplicationDetail, state: initiated", kv("applicationId", req.getApplicationId()), kv("request", request));
        final ObjectResponse<GetApplicationDetailResponse> response = new ObjectResponse<>(applicationDetailService.getApplicationDetail(req));
        logger.info("", kv("action", "getApplicationDetail"), kv("state", "succeeded"), kv("applicationId", req.getApplicationId()));
        logger.debug("action: getApplicationDetail, state: succeeded", kv("applicationId", req.getApplicationId()), kv("response", response));
        return response;
    }

    /**
     * Lookup application by app key.
     *
     * @param request Application detail request.
     * @return Application detail response.
     * @throws Exception In case the service throws exception.
     */
    @PostMapping("/detail/version")
    public ObjectResponse<LookupApplicationByAppKeyResponse> lookupApplicationByAppKey(@Valid @RequestBody ObjectRequest<LookupApplicationByAppKeyRequest> request) throws Exception {
        final LookupApplicationByAppKeyRequest req = request.getRequestObject();
        logger.info("", kv("action", "lookupApplicationByAppKey"), kv("state", "initiated"), kv("applicationKey", req.getApplicationKey()));
        logger.debug("action: lookupApplicationByAppKey, state: initiated", kv("applicationKey", req.getApplicationKey()), kv("request", request));
        final ObjectResponse<LookupApplicationByAppKeyResponse> response = new ObjectResponse<>(applicationService.lookupApplicationByAppKey(req));
        logger.info("", kv("action", "lookupApplicationByAppKey"), kv("state", "succeeded"), kv("applicationKey", req.getApplicationKey()));
        logger.debug("action: lookupApplicationByAppKey, state: succeeded", kv("applicationKey", req.getApplicationKey()), kv("response", response));
        return response;
    }

}
