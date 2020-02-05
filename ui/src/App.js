import React, {Component} from 'react';
import {Button, Dropdown} from 'semantic-ui-react';
import {Line} from 'react-chartjs-2';
import 'chartjs-plugin-colorschemes';
import './App.css';

class App extends Component {
    constructor(props) {
        super(props);
        this.state = {
            isFetching: false,
            isLoading: false,
            availableWoofs: [],
            selectedWoofs: [],
            retentionMinutes: 60,
            loadedRetentionMinutes: 60,
            datasets: []
        };
    }

    getState() {
        return this.state
    }

    componentDidMount() {
        const that = this;
        const chartContainer = document.getElementById('chart-wrapper');
        const dataCanvas = document.getElementById('data-chart');
        const cursorCanvas = document.getElementById('cursor');
        const cursorCtx = cursorCanvas.getContext('2d');

        chartContainer.onmousemove = (e) => {
            cursorCanvas.width = dataCanvas.width;
            cursorCanvas.height = dataCanvas.height;
            const { offsetLeft, offsetTop} = cursorCanvas;
            const {clientX, clientY} = e;
            cursorCtx.clearRect(0, 0, cursorCanvas.width, cursorCanvas.height);
            if (clientX <= offsetLeft + dataCanvas.width && clientX >= 48 && clientY <= offsetTop + dataCanvas.height && clientY >= offsetTop) {
                cursorCtx.beginPath();
                cursorCtx.moveTo(clientX - offsetLeft, that.state.datasets.length > 0 ? 32 : 10);
                cursorCtx.lineTo(clientX - offsetLeft, dataCanvas.height - 30);
                cursorCtx.stroke();
            }
        };
        chartContainer.onmouseleave = () => {
            cursorCtx.clearRect(0, 0, cursorCanvas.width, cursorCanvas.height);
        };

        this.setState({isFetching: true});
        fetch('http://localhost:8080/api/woofs')
            .then(response => {
                return response.json();
            })
            .then(results => {
                this.setState({availableWoofs: results, isFetching: false});
            });
    }

    handleLoad = () => {
        const {selectedWoofs, retentionMinutes, availableWoofs} = this.state;

        const aggIntervals = [
            {
                geq: 6 * 30 * 24 * 60,
                interval: 'week'
            },
            {
                geq: 30 * 24 * 60,
                interval: 'day'
            },
            {
                geq: 7 * 24 * 60,
                interval: 'hour'
            },
            {
                geq: 0,
                interval: 'minute'
            }
        ];

        const interval = aggIntervals.find(cfg => retentionMinutes >= cfg.geq).interval;

        this.setState({isLoading: true});
        Promise.all(
            selectedWoofs.map(woofId =>
                fetch(
                    `http://localhost:8080/api/query?woofId=${woofId}&from=${Date.now() - retentionMinutes * 60 * 1000}&to=${Date.now()}&aggregation=average&interval=${interval}`
                ).then(response => response.json())
                    .then(results => {
                        const woofRef = availableWoofs.find(w => w.id === woofId);
                        return {
                            label: woofRef.name + ' ' + woofRef.dataType,
                            fill: false,
                            borderJoinStyle: 'round',
                            lineTension: 0,
                            pointRadius: 3,
                            pointHoverRadius: 6,
                            data: results.map(woof => {
                                return {
                                    x: woof.timestamp,
                                    y: woof.value
                                };
                            })
                        };
                    })
            )
        ).then(results => {
            this.setState({isLoading: false, datasets: results, loadedRetentionMinutes: retentionMinutes});
        });
    };

    handleRangeSelect = (event, data) => {
        this.setState({retentionMinutes: data.value});
    };

    handleSourceSelect = (event, data) => {
        this.setState({selectedWoofs: data.value});
    };

    render() {
        const {isFetching, isLoading, availableWoofs, datasets, loadedRetentionMinutes} = this.state;
        const results = availableWoofs.map(woof => {
            return {
                key: woof.id,
                value: woof.id,
                text: woof.name + ' ' + woof.dataType
            };
        });

        const data = {datasets: datasets};
        const dateFormats = [
            {
                geq: 6 * 30 * 24 * 60,
                unit: 'month'
            },
            {
                geq: 3 * 24 * 60,
                unit: 'day'
            },
            {
                geq: 0,
                unit: 'hour'
            }
        ];

        const format = dateFormats.find(cfg => loadedRetentionMinutes >= cfg.geq);

        const customFormat = ('formatStr' in format) ? {[format.unit]: format.formatStr} : {};

        const options = {
            plugins: {
                colorschemes: {
                    scheme: 'office.Berlin6'
                }
            },
            responsive: true,
            tooltips: {
                mode: 'nearest',
                intersect: false,
                axis: 'x'
            },
            hover: {
                mode: 'nearest',
                intersect: false,
                axis: 'x'
            },
            scales: {
                xAxes: [
                    {
                        type: 'time',
                        bounds: 'ticks',
                        time: {
                            displayFormats: customFormat,
                            unit: format.unit
                        },
                        ticks: {
                            maxTicksLimit: 12,
                            autoSkip: true,
                            min: Date.now() - (loadedRetentionMinutes * 60 * 1000),
                            max: Date.now()
                        },
                        distribution: 'linear'
                    }
                ]
            }
        };

        const ranges = [
            {
                value: 60,
                text: 'Past hour'
            },
            {
                value: 6 * 60,
                text: 'Past 6 hours'
            },
            {
                value: 24 * 60,
                text: 'Past day'
            },
            {
                value: 7 * 24 * 60,
                text: 'Past week'
            },
            {
                value: 31 * 24 * 60,
                text: 'Past month'
            },
            {
                value: 365 * 24 * 60,
                text: 'Past year'
            }
        ];

        // const intervals = [
        //     {
        //         value: 'minute',
        //         text: 'minute'
        //     },
        //     {
        //         value: 'hour',
        //         text: 'hour'
        //     },
        //     {
        //         value: 'day',
        //         text: 'day'
        //     },
        //     {
        //         value: 'week',
        //         text: 'week'
        //     },
        //     {
        //         value: 'month',
        //         text: 'month'
        //     }
        // ];
        //
        // const aggregations = [
        //     {
        //         value: 'average',
        //         text: 'average'
        //     },
        //     {
        //         value: 'count',
        //         text: 'count'
        //     },
        //     {
        //         value: 'max',
        //         text: 'max'
        //     },
        //     {
        //         value: 'min',
        //         text: 'min'
        //     },
        //     {
        //         value: 'sum',
        //         text: 'sum'
        //     }
        // ];

        return (
            <div className='container'>
                <span className='datasource-select'>
                    <Dropdown
                        placeholder='Data source'
                        fluid
                        multiple
                        search
                        selection
                        loading={isFetching || isLoading}
                        disabled={isFetching || isLoading}
                        options={results}
                        onChange={this.handleSourceSelect}
                    />
                </span>
                <span className='range-select'>
                    <Dropdown
                        placeholder='Range'
                        fluid
                        selection
                        loading={isLoading}
                        disabled={isLoading}
                        options={ranges}
                        onChange={this.handleRangeSelect}
                        defaultValue={ranges[0].value}
                    />
                </span>
                {/*<div className='agg-interval-select'>*/}
                {/*  <Dropdown*/}
                {/*      placeholder='Aggregation interval'*/}
                {/*      fluid*/}
                {/*      selection*/}
                {/*      loading={isLoading}*/}
                {/*      disabled={isLoading}*/}
                {/*      options={intervals}*/}
                {/*      onChange={this.handleIntervalSelect}*/}
                {/*      defaultValue={intervals[0].value}*/}
                {/*  />*/}
                {/*</div>*/}
                {/*<div className='agg-type-select'>*/}
                {/*  <Dropdown*/}
                {/*      placeholder='Aggregation type'*/}
                {/*      fluid*/}
                {/*      selection*/}
                {/*      loading={isLoading}*/}
                {/*      disabled={isLoading}*/}
                {/*      options={aggregations}*/}
                {/*      onChange={this.handleAggregationSelect}*/}
                {/*      defaultValue={aggregations[0].value}*/}
                {/*  />*/}
                {/*</div>*/}
                <span className='plot-button'>
                    <Button
                        loading={isLoading}
                        disabled={isLoading}
                        onClick={this.handleLoad}
                    >
                        Plot
                    </Button>
                </span>
                <br style={{clear: 'both'}}/>
                <div className='chart-container' id='chart-wrapper'>

                    <canvas
                        id='cursor'
                    />
                    <Line id='data-chart' data={data} options={options}/>

                </div>
            </div>
        );
    }
}

export default App;
