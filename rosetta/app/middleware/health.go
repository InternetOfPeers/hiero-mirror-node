// SPDX-License-Identifier: Apache-2.0

package middleware

import (
	"context"
	"fmt"
	"time"

	"github.com/coinbase/rosetta-sdk-go/client"
	"github.com/coinbase/rosetta-sdk-go/server"
	"github.com/coinbase/rosetta-sdk-go/types"
	"github.com/hellofresh/health-go/v4"
	"github.com/hellofresh/health-go/v4/checks/postgres"
	"github.com/hiero-ledger/hiero-mirror-node/rosetta/app/config"
	log "github.com/sirupsen/logrus"
)

const (
	livenessPath  = "/health/liveness"
	readinessPath = "/health/readiness"
)

// healthController holds data used to response to health probes
type healthController struct {
	livenessHealth  *health.Health
	readinessHealth *health.Health
}

// NewHealthController creates a new HealthController object
func NewHealthController(rosettaConfig *config.Config) (server.Router, error) {
	livenessHealth, err := health.New()
	if err != nil {
		return nil, err
	}

	readinessChecks := []health.Config{
		{
			Name:      "postgresql",
			Timeout:   time.Second * 10,
			SkipOnErr: false,
			Check:     postgres.New(postgres.Config{DSN: rosettaConfig.Db.GetDsn()}),
		},
		{
			Name:      "network",
			Timeout:   time.Second * 10,
			SkipOnErr: false,
			Check:     checkNetworkStatus(rosettaConfig.Port),
		},
	}
	readinessHealth, err := health.New(health.WithChecks(readinessChecks...))
	if err != nil {
		return nil, err
	}

	return &healthController{
		livenessHealth:  livenessHealth,
		readinessHealth: readinessHealth,
	}, nil
}

// Routes returns the Health controller routes
func (c *healthController) Routes() server.Routes {
	return server.Routes{
		{
			"liveness",
			"GET",
			livenessPath,
			c.livenessHealth.HandlerFunc,
		},
		{
			"readiness",
			"GET",
			readinessPath,
			c.readinessHealth.HandlerFunc,
		},
	}
}

func checkNetworkStatus(port uint16) func(ctx context.Context) error {
	serverUrl := fmt.Sprintf("http://localhost:%d", port)
	cfg := client.NewConfiguration(serverUrl, "readiness-check", nil)
	rosettaClient := client.NewAPIClient(cfg)

	return func(ctx context.Context) (checkErr error) {
		networkList, _, err := rosettaClient.NetworkAPI.NetworkList(ctx, &types.MetadataRequest{})
		if err != nil {
			log.Errorf("Readiness check, /network/list failed: %v", err)
			return err
		}

		network := networkList.NetworkIdentifiers[0]
		_, _, err = rosettaClient.NetworkAPI.NetworkStatus(ctx, &types.NetworkRequest{NetworkIdentifier: network})
		if err != nil {
			log.Errorf("Readiness check, /network/status failed: %v", err)
			return err
		}

		return
	}
}
