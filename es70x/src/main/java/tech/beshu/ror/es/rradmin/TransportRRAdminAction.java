/*
 *    This file is part of ReadonlyREST.
 *
 *    ReadonlyREST is free software: you can redistribute it and/or modify
 *    it under the terms of the GNU General Public License as published by
 *    the Free Software Foundation, either version 3 of the License, or
 *    (at your option) any later version.
 *
 *    ReadonlyREST is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *    GNU General Public License for more details.
 *
 *    You should have received a copy of the GNU General Public License
 *    along with ReadonlyREST.  If not, see http://www.gnu.org/licenses/
 */

package tech.beshu.ror.es.rradmin;

import com.google.common.util.concurrent.FutureCallback;
import io.vavr.control.Option;
import monix.execution.Scheduler;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.support.ActionFilters;
import org.elasticsearch.action.support.HandledTransportAction;
import org.elasticsearch.client.node.NodeClient;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.tasks.Task;
import org.elasticsearch.transport.TransportService;
import tech.beshu.ror.boot.SchedulerPools$;
import tech.beshu.ror.configuration.ConfigLoader;
import tech.beshu.ror.configuration.ConfigLoader.ConfigLoaderError;
import tech.beshu.ror.configuration.IndexConfigLoader;
import tech.beshu.ror.configuration.IndexConfigLoader.IndexConfigError;
import tech.beshu.ror.configuration.IndexConfigLoader.IndexConfigError$;
import tech.beshu.ror.es.EsIndexContentProvider;
import tech.beshu.ror.es.ResponseActionListener;
import tech.beshu.ror.es.__old_SettingsObservableImpl;
import tech.beshu.ror.settings.RawSettings;
import tech.beshu.ror.settings.SettingsUtils;
import tech.beshu.ror.utils.YamlOps$;

import static tech.beshu.ror.Constants.REST_CONFIGURATION_FILE_PATH;
import static tech.beshu.ror.Constants.REST_CONFIGURATION_PATH;
import static tech.beshu.ror.Constants.REST_METADATA_PATH;
import static tech.beshu.ror.Constants.REST_REFRESH_PATH;

public class TransportRRAdminAction extends HandledTransportAction<RRAdminRequest, RRAdminResponse> {

  // todo: fixme
  private final __old_SettingsObservableImpl settingsObservable = null;

  private final Scheduler adminRestApiScheduler = SchedulerPools$.MODULE$.adminRestApiScheduler();
  private final IndexConfigLoader indexConfigLoader;

  @Inject
  public TransportRRAdminAction(TransportService transportService, ActionFilters actionFilters, NodeClient nodeClient) {
    super(RRAdminAction.NAME, transportService, actionFilters, RRAdminRequest::new);
    this.indexConfigLoader = new IndexConfigLoader(new EsIndexContentProvider(nodeClient));
  }

  @Override
  protected void doExecute(Task task, RRAdminRequest request, ActionListener<RRAdminResponse> listener) {
    try {
      forceRefreshRorConfigEndpoint(request, listener)
          .orElse(() -> updateIndexConfiguration(request, listener))
          .orElse(() -> getIndexConfiguration(request, listener))
          .orElse(() -> getMetadata(request, listener))
          .getOrElseGet(() -> rejectedRequest(listener));
    } catch (Exception e) {
      listener.onResponse(new RRAdminResponse(e));
    }
  }

  private Option<Void> forceRefreshRorConfigEndpoint(RRAdminRequest request,
      ActionListener<RRAdminResponse> listener) {
    return Option.when(
        isHttpPost(request) && matchesPath(REST_REFRESH_PATH, request),
        () -> {
          settingsObservable.refreshFromIndex();
          listener.onResponse(new RRAdminResponse("ok refreshed"));
          return null;
        });
  }

  private Option<Void> updateIndexConfiguration(RRAdminRequest request,
      ActionListener<RRAdminResponse> listener) {
    return Option.when(
        isHttpPost(request) && matchesPath(REST_CONFIGURATION_PATH, request),
        () -> {
          String body = request.getContent();
          if (body.length() == 0) {
            listener.onFailure(new Exception("empty body"));
          } else {
            // todo: validate and save
            settingsObservable.refreshFromStringAndPersist(
                new RawSettings(SettingsUtils.extractYAMLfromJSONStorage(body), settingsObservable.getCurrent().getLogger()), new FutureCallback() {
                  @Override
                  public void onSuccess(Object result) {
                    listener.onResponse(new RRAdminResponse("updated settings"));
                  }

                  @Override
                  public void onFailure(Throwable t) {
                    listener.onFailure(new Exception("could not update settings ", t));
                  }
                });
          }
          return null;
        });
  }

  private Option<Void> getIndexConfiguration(RRAdminRequest request,
      ActionListener<RRAdminResponse> listener) {
    return Option.when(
        isHttpGet(request) && (matchesPath(REST_CONFIGURATION_FILE_PATH, request) || matchesPath(REST_CONFIGURATION_PATH, request)),
        () -> {
          indexConfigLoader
              .load()
              .runAsync(
                  result -> {
                    if(result.isRight()) {
                      if(result.right().get().isRight()) {
                        ConfigLoader.RawRorConfig rawRorConfig = result.right().get().right().get();
                        String yaml = YamlOps$.MODULE$.jsonToYamlString(rawRorConfig.rawConfig());
                        listener.onResponse(new RRAdminResponse(yaml));
                      } else {
                        ConfigLoaderError<IndexConfigError> error = result.right().get().left().get();
                        String errorMessage = IndexConfigError$.MODULE$.indexConfigLoaderErrorShow().show(error);
                        listener.onResponse(new RRAdminResponse(errorMessage));
                      }
                    } else {
                      Throwable throwable = result.left().get();
                      listener.onFailure(new Exception(throwable));
                    }
                    return null;
                  },
                  adminRestApiScheduler
              );
          return null;
        }
    );
  }

  private Option<Void> getMetadata(RRAdminRequest request,
      ActionListener<RRAdminResponse> listener) {
    return Option.when(
        isHttpGet(request) && matchesPath(REST_METADATA_PATH, request),
        () -> {
          listener.onResponse(new RRAdminResponse("will be filled in " + ResponseActionListener.class.getSimpleName()));
          return null;
        }
    );
  }

  private Void rejectedRequest(ActionListener<RRAdminResponse> listener) {
    listener.onFailure(new Exception("Didn't find anything to handle this request"));
    return null;
  }

  private boolean matchesPath(String path, RRAdminRequest request) {
    return path.equals(normalisePath(request.getPath()));
  }

  private boolean isHttpPost(RRAdminRequest request) {
    return isHttpMethod("POST", request);
  }

  private boolean isHttpGet(RRAdminRequest request) {
    return isHttpMethod("GET", request);
  }

  private boolean isHttpMethod(String method, RRAdminRequest request) {
    return method.equals(request.getMethod().toUpperCase());
  }

  private String normalisePath(String s) {
    return s.substring(0, s.length() - (s.endsWith("/") ? 1 : 0));
  }

}
